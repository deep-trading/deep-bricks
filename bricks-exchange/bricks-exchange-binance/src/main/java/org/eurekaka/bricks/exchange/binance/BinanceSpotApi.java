package org.eurekaka.bricks.exchange.binance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.ExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.Mac;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinanceSpotApi implements ExApi {
    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("+00"));

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;
    private final ObjectReader reader;

    public BinanceSpotApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;
        this.reader = Utils.mapper.reader().forType(BinanceRestV1.class);
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        return null;
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        return new ArrayList<>();
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        return new ArrayList<>();
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        return null;
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        return null;
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        return null;
    }

    @Override
    public void transferAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            String path;
            if (transfer.type == 0 && transfer.toAccountConfig != null) {
                // 子母账户间转账
                String fromAccountType = transfer.fromAccountConfig.getProperty("account_type");
                String toAccountType = transfer.toAccountConfig.getProperty("account_type");
                String fromEmail = transfer.fromAccountConfig.getProperty("account_email");
                String toEmail = transfer.toAccountConfig.getProperty("account_email");
                if (fromAccountType == null || toAccountType == null || fromEmail == null || toEmail == null) {
                    throw new ExApiException("account config parameters missing");
                }
                params.put("fromEmail", fromEmail);
                params.put("toEmail", toEmail);
                params.put("fromAccountType", fromAccountType);
                params.put("toAccountType", toAccountType);

                path = "/sapi/v1/sub-account/universalTransfer";
            } else if (transfer.toAccountConfig == null && transfer.type > 0) {
                // 账户内部转账
                params.put("type", transferType(transfer.type));
                path = "/sapi/v1/asset/transfer";
            } else {
                throw new ExApiException("unknown transfer operation");
            }

            params.put("asset", transfer.asset);
            params.put("amount", String.valueOf(transfer.amount));
            String url = generateSignedUrl(path, params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());

            if (result.code != 0 || result.tranId == null) {
                throw new ExApiException(result.msg);
            }
        } catch (Exception e) {
            throw new ExApiException("failed to transfer", e);
        }
    }

    @Override
    public void withdrawAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();

            String address = transfer.toAccountConfig.getProperty("address");
            String chainType = transfer.toAccountConfig.getProperty("network", "TRX");
            String transactionFeeFlag = transfer.toAccountConfig.getProperty("fee_flag", "false");

            params.put("coin", transfer.asset);
            params.put("amount", String.valueOf(transfer.amount));
            params.put("network", chainType);
            params.put("address", address);
            params.put("transactionFeeFlag", transactionFeeFlag);

            String url = generateSignedUrl("/sapi/v1/capital/withdraw/apply", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());

            if (result.code != 0 || result.id == null) {
                throw new ExApiException(result.code + result.msg);
            }
        } catch (Exception e) {
            throw new ExApiException("failed to withdraw asset", e);
        }
    }

    @Override
    public List<AccountAssetRecord> getAssetRecords(AssetTransferHistory transferHistory) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            String path = transferHistory.type == 1 ? "/sapi/v1/capital/deposit/hisrec" :
                    "/sapi/v1/capital/withdraw/history";
            params.put("startTime", String.valueOf(transferHistory.start));
            params.put("endTime", String.valueOf(transferHistory.stop));
            params.put("limit", String.valueOf(transferHistory.limit));

            List<AccountAssetRecord> records = new ArrayList<>();
            String url = generateSignedUrl(path, params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BinanceAssetData> result = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
            for (BinanceAssetData data : result) {
                AccountAssetRecord record;
                if (transferHistory.type == 2) {
                    // 提币历史
                    long time = ZonedDateTime.parse(data.applyTime, formatter).toEpochSecond() * 1000;
                    String status;
                    if (data.status == 6) {
                        status = AccountAssetRecord.SUCCESS_STATUS;
                    } else if (data.status == 5) {
                        status = AccountAssetRecord.FAILED_STATUS;
                    } else if (data.status == 3) {
                        status = AccountAssetRecord.REJECTED_STATUS;
                    } else if (data.status == 1) {
                        status = AccountAssetRecord.CANCELLED_STATUS;
                    } else {
                        status = AccountAssetRecord.PROCESSING_STATUS;
                    }
                    record = new AccountAssetRecord(time, data.coin, data.amount,
                            status, data.address.substring(0 ,16));
                } else {
                    // 充值历史
                    String status = data.status == 1 ?
                            AccountAssetRecord.SUCCESS_STATUS : AccountAssetRecord.FAILED_STATUS;
                    record = new AccountAssetRecord(data.insertTime, data.coin,
                            data.amount, status, data.address.substring(0, 16));
                }
                records.add(record);
            }

            return  records;
        } catch (Exception e) {
            throw new ExApiException("failed to get account info", e);
        }
    }

    private String generateSignedUrl(String path, Map<String, String> params) {
        params.put("recvWindow", "5000");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String paramString = HttpUtils.param2String(params);
        // 该对象非线程安全，而且每次生成新对象并不影响性能
        Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
        String signature = Utils.encodeHexString(sha256Mac.doFinal(paramString.getBytes()));
        return accountConfig.getUrl() + path + "?" + paramString + "&signature=" + signature;
    }


    private String transferType(int type) {
        switch (type) {
            case 1:
                return "MAIN_UMFUTURE";
            case 2:
                return "UMFUTURE_MAIN";
            default:
                return "UNKNOWN";
        }
    }


}
