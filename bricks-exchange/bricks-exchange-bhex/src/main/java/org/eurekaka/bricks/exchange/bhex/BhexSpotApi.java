package org.eurekaka.bricks.exchange.bhex;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BhexSpotApi implements ExApi {

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;

    public BhexSpotApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;
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
        try {
            List<AccountValue> accountValues = new ArrayList<>();
            String url = generateSignedUrl("/openapi/v1/account", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestResp resp = Utils.mapper.readValue(response.body(), BhexRestResp.class);

            if (resp.code != 0) {
                throw new ExApiException("return code is not 0, resp: " + response.body());
            }
            for (BhexRestBalance balance : resp.balances) {
                accountValues.add(new AccountValue(balance.asset, accountConfig.getName(),
                        balance.free + balance.locked, balance.free));
            }
            return accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", order.getSymbol());
            params.put("quantity", String.valueOf(order.getSize()));
            params.put("side", order.getSide().name());
            if (order.getOrderType().equals(OrderType.LIMIT)) {
                params.put("type", "LIMIT");
            } else if (order.getOrderType().equals(OrderType.LIMIT_MOCK)) {
                params.put("type", "COM");
            } else if (order.getOrderType().equals(OrderType.LIMIT_MAKER)) {
                params.put("type", "LIMIT_MAKER");
            }
            params.put("price", String.valueOf(order.getPrice()));
            params.put("newClientOrderId", order.getSymbol() + System.nanoTime());
            String url = generateSignedUrl("/openapi/v1/order", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestResp resp = Utils.mapper.readValue(response.body(), BhexRestResp.class);
            if (resp.code != 0) {
                if (resp.code == -2010) {
                    return OrderResultValue.FAIL_OK.name();
                }
                throw new ExApiException("resp code is not 0, resp: " + resp);
            }
            return resp.orderId;
        } catch (Exception e) {
            throw new ExApiException("failed to make order: " + order, e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", symbol);
            if (type == 1) {
                params.put("side", "BUY");
            } else if (type == 2) {
                params.put("side", "SELL");
            }
            params.put("limit", "100");
            String url = generateSignedUrl("/openapi/v1/openOrders", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BhexRestOrder> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            List<CurrentOrder> orders = new ArrayList<>();
            for (BhexRestOrder bhexRestOrder : resp) {
                if ("NEW".equals(bhexRestOrder.status) || "PARTIALLY_FILLED".equals(bhexRestOrder.status)) {
                    int myType = 0;
                    OrderSide side = OrderSide.NONE;
                    if ("BUY".equals(bhexRestOrder.side)) {
                        myType = 1;
                        side = OrderSide.BUY;
                    } else if ("SELL".equals(bhexRestOrder.side)) {
                        myType = 2;
                        side = OrderSide.SELL;
                    }
                    if (type == 0 || type == myType) {
                        orders.add(new CurrentOrder(bhexRestOrder.orderId, null,
                                bhexRestOrder.symbol, side, OrderType.LIMIT, bhexRestOrder.origQty,
                                bhexRestOrder.price, bhexRestOrder.executedQty));
                    }
                }
            }
            return orders;
        } catch (Exception e) {
            throw new ExApiException("failed to get bhex current orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("orderId", orderId);
            params.put("fastCancel", "1");
            String url = generateSignedUrl("/openapi/v1/order", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .DELETE()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestResp resp = Utils.mapper.readValue(response.body(), BhexRestResp.class);

            if (resp.code != 0 && resp.code != -2013 && resp.code != -1139) {
                throw new ExApiException("resp code is not 0, resp: " + resp);
            }
            // 返回空当前订单
            return new CurrentOrder(orderId, null, symbol, OrderSide.NONE,
                    OrderType.LIMIT, 0, 0, 0);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order, symbol: " + symbol + ", order id: " + orderId, e);
        }
    }

    @Override
    public void transferAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            String fromAccountParent = transfer.fromAccountConfig.getProperty("parent_account");
            String toAccountParent = transfer.toAccountConfig.getProperty("parent_account");

            if (fromAccountParent != null && toAccountParent != null) {
                throw new ExApiException("can not transfer between sub accounts");
            }

            if (fromAccountParent != null) {
                // 子账户转母账户
                String fromAccountType = transfer.fromAccountConfig.getProperty("account_type", "1");
                String fromAccountIndex = transfer.fromAccountConfig.getProperty("account_index");

                params.put("fromAccountType", fromAccountType);
                params.put("fromAccountIndex", fromAccountIndex);
            } else if (toAccountParent != null) {
                // 母账户转子账户
                String toAccountType = transfer.toAccountConfig.getProperty("account_type", "1");
                String toAccountIndex = transfer.toAccountConfig.getProperty("account_index");

                params.put("toAccountType", toAccountType);
                params.put("toAccountIndex", toAccountIndex);
            }

            params.put("tokenId", transfer.asset);
            params.put("amount", String.valueOf(transfer.amount));

            String url = generateSignedUrl("/openapi/v1/transfer", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestResp resp = Utils.mapper.readValue(response.body(), BhexRestResp.class);
            if (resp.code != 0) {
                throw new ExApiException("resp code is not 0, resp: " + resp);
            }
        } catch (Exception e) {
            throw new ExApiException("failed to transfer asset", e);
        }
    }

    @Override
    public void withdrawAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            String fromAccountParent = transfer.fromAccountConfig.getProperty("parent_account");
            String transferId = transfer.fromAccountConfig.getName() + transfer.toAccountConfig.getName() +
                    transfer.asset + Math.round(transfer.amount) + System.currentTimeMillis();
            String address = transfer.toAccountConfig.getProperty("address");
            String chainType = transfer.toAccountConfig.getProperty("network", "TRC20");
            if (address == null) {
                throw new ExApiException("missing address");
            }

            if (fromAccountParent == null) {
                params.put("address", address);
                params.put("chainType", chainType);
                params.put("clientOrderId", transferId);
                params.put("tokenId", transfer.asset);
                params.put("withdrawQuantity", String.valueOf(transfer.amount));

                String url = generateSignedUrl("/openapi/v1/withdraw", params);
                HttpRequest request = HttpRequest.newBuilder(new URI(url))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .header("X-BH-APIKEY", accountConfig.getAuthKey())
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                BhexRestResp resp = Utils.mapper.readValue(response.body(), BhexRestResp.class);
                if (resp.code != 0 || resp.orderId == null) {
                    throw new ExApiException("resp code is not 0, resp: " + resp);
                }
            }
        } catch (Exception e) {
            throw new ExApiException("failed to withdraw asset", e);
        }
    }

    @Override
    public List<AccountAssetRecord> getAssetRecords(AssetTransferHistory transferHistory) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("startTime", String.valueOf(transferHistory.start));
            params.put("endTime", String.valueOf(transferHistory.stop));
            params.put("limit", String.valueOf(transferHistory.limit));
            String action;
            if (transferHistory.type == 1) {
                action = "depositOrders";
            } else if (transferHistory.type == 2) {
                action = "withdrawalOrders";
            } else {
                throw new ExApiException("unknown asset records type");
            }

            String url = generateSignedUrl("/openapi/v1/" + action, params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BhexRestData> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            List<AccountAssetRecord> records = new ArrayList<>();
            for (BhexRestData data : resp) {
                String status = transferHistory.type == 1 ?
                        AccountAssetRecord.SUCCESS_STATUS : withdrawAssetStatus(data.status);
                records.add(new AccountAssetRecord(data.time, data.tokenId,
                        data.quantity, status, data.address.substring(0, 16)));
            }
            return records;
        } catch (Exception e) {
            throw new ExApiException("failed to get asset records", e);
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

    private String withdrawAssetStatus(int status) {
        switch (status) {
            case 1:
            case 3:
            case 5:
                return AccountAssetRecord.PROCESSING_STATUS;
            case 2:
            case 4:
                return AccountAssetRecord.REJECTED_STATUS;
            case 6:
                return AccountAssetRecord.SUCCESS_STATUS;
            default:
                return AccountAssetRecord.FAILED_STATUS;
        }
    }

    static class BhexRestResp {
        public int code;
        public String msg;

        public List<BhexRestBalance> balances;

        public String orderId;

        public BhexRestResp() {
        }

        @Override
        public String toString() {
            return "BhexRestResp{" +
                    "code=" + code +
                    ", msg='" + msg + '\'' +
                    ", balances=" + balances +
                    ", orderId='" + orderId + '\'' +
                    '}';
        }
    }

    static class BhexRestBalance {
        public String asset;
        public double free;
        public double locked;

        public BhexRestBalance() {
        }

        @Override
        public String toString() {
            return "BhexRestBalance{" +
                    "asset='" + asset + '\'' +
                    ", free=" + free +
                    ", locked=" + locked +
                    '}';
        }
    }


    static class BhexRestOrder {
        public String orderId;
        public String symbol;
        public double price;
        public double origQty;
        public double executedQty;
        public String side;
        public String status;

        public BhexRestOrder() {
        }

        @Override
        public String toString() {
            return "BhexRestOrder{" +
                    "orderId='" + orderId + '\'' +
                    ", symbol='" + symbol + '\'' +
                    ", price=" + price +
                    ", origQty=" + origQty +
                    ", executedQty=" + executedQty +
                    ", side='" + side + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }

    static class BhexRestData {
        public long time;
        public String tokenId;
        public double quantity;
        public int status;
        public String address;

        @Override
        public String toString() {
            return "BhexRestData{" +
                    "time=" + time +
                    ", tokenId='" + tokenId + '\'' +
                    ", quantity=" + quantity +
                    ", status=" + status +
                    ", address='" + address + '\'' +
                    '}';
        }
    }

}
