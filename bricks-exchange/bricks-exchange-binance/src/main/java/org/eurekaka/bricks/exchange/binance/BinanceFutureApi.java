package org.eurekaka.bricks.exchange.binance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.Mac;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class BinanceFutureApi implements FutureExApi {
    private final HttpClient httpClient;
    private final AccountConfig accountConfig;

    private final ObjectReader reader;

    public BinanceFutureApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;
        this.reader = Utils.mapper.reader().forType(BinanceRestV1.class);
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        try {
            String url = generateSignedUrl("/fapi/v1/listenKey", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());

            if (result.listenKey == null) {
                throw new Exception("listenKey is null");
            }
            BinanceSocketSub sub = new BinanceSocketSub("SUBSCRIBE", result.listenKey);
            return Utils.mapper.writeValueAsString(sub);
        } catch (Exception e) {
            throw new ExApiException("can not get listenKey", e);
        }
    }

    @Override
    public List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        // 返回所有仓位信息
        try {
            List<PositionValue> positionValues = new ArrayList<>();
            String url = generateSignedUrl("/fapi/v2/positionRisk", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BinancePositionData> positionData = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
            for (BinancePositionData data : positionData) {
                positionValues.add(new PositionValue(data.symbol, accountConfig.getName(),
                        data.positionAmt, data.markPrice, Math.round(data.positionAmt * data.markPrice),
                        data.entryPrice, data.unRealizedProfit));
            }
            return positionValues;
        } catch (Throwable t) {
            throw new ExApiException("failed to get position values", t);
        }
    }

    @Override
    public List<NetValue> getNetValue(String symbol) throws ExApiException {
        try {
            List<NetValue> netValues = new ArrayList<>();
            String url = generateUrl("/fapi/v1/premiumIndex", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            List<BinanceSymbolInfo> symbolInfos = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
            for (BinanceSymbolInfo info : symbolInfos) {
                netValues.add(new NetValue(info.symbol,
                        accountConfig.getName(), Math.round(info.markPrice * PRECISION)));
            }
            return netValues;
        } catch (Throwable t) {
            throw new ExApiException("failed to get exchange infos", t);
        }
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        try {
            List<ExSymbol> symbols = new ArrayList<>();
            String url = generateUrl("/fapi/v1/exchangeInfo", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());
            for (BinanceSymbolInfo info : result.symbols) {
                double quantityPrecision = Math.pow(10, info.quantityPrecision);
                double pricePrecision = Math.pow(10, info.pricePrecision);
                symbols.add(new ExSymbol(info.symbol, pricePrecision, quantityPrecision));
            }
            return symbols;
        } catch (Throwable t) {
            throw new ExApiException("failed to get exchange infos", t);
        }
    }

    @Override
    public List<KLineValue> getKLineValues(KLineValuePair pair) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", pair.symbol);
            params.put("interval", pair.interval.value);
            params.put("startTime", String.valueOf(pair.startTime));
            params.put("endTime", String.valueOf(pair.stopTime));
            params.put("limit", String.valueOf(pair.limit));

            String url = generateUrl("/fapi/v1/klines", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<List<String>> result = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
            List<KLineValue> lineValues = new ArrayList<>();
            for (List<String> kline : result) {
                long time = Long.parseLong(kline.get(0));
                double open = Double.parseDouble(kline.get(1));
                double highest = Double.parseDouble(kline.get(2));
                double lowest = Double.parseDouble(kline.get(3));
                double close = Double.parseDouble(kline.get(4));
                double volume = Double.parseDouble(kline.get(5));
                lineValues.add(new KLineValue(time, pair.name, pair.symbol, open, close, highest, lowest, volume));
            }
            return lineValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get kline values: " + pair, e);
        }
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        try {
            List<AccountValue> accountValues = new ArrayList<>();
            String url = generateSignedUrl("/fapi/v2/account", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());
            if (result.assets != null) {
                for (BinanceAssetData data : result.assets) {
                    double totalBalance = data.walletBalance + data.unrealizedProfit;
                    accountValues.add(new AccountValue(data.asset, accountConfig.getName(),
                            totalBalance, data.walletBalance, data.availableBalance));
                }
            }
            return  accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account info", e);
        }
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", order.getSymbol());
            params.put("side", order.getSide().name());
            params.put("type", order.getOrderType().name());
            double size = order.getSize();
            if (size == 0) {
                return OrderResultValue.FAIL_OK.name();
            }
            params.put("quantity", String.format("%f", size));
            if (OrderType.LIMIT.equals(order.getOrderType())) {
                params.put("price", String.valueOf(order.getPrice()));
                params.put("timeInForce", "GTX");
            }
//            if (newClientOrderId != null) {
//                params.put("newClientOrderId", newClientOrderId);
//            }
            String url = generateSignedUrl("/fapi/v1/order", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());

            if (result.code != 0 || result.orderId == null) {
                throw new IOException("returned order id is null, resp: " + response.body());
            }
            return result.orderId;
        } catch (Exception e) {
            throw new ExApiException("failed to make order.", e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", symbol);

            String url = generateSignedUrl("/fapi/v1/openOrders", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BinanceOrder> result = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            List<CurrentOrder> orders = new ArrayList<>();
            for (BinanceOrder order : result) {
                orders.add(new CurrentOrder(order.orderId, order.symbol,
                        OrderSide.valueOf(order.side),
                        OrderType.valueOf(order.type),
                        order.origQty, order.price, order.executedQty));
            }
            return orders;
        } catch (Exception e) {
            throw new ExApiException("failed to get current open orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", symbol);
            params.put("orderId", orderId);
            String url = generateSignedUrl("/fapi/v1/order", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .DELETE()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceOrder result = Utils.mapper.readValue(response.body(), BinanceOrder.class);

            if (result.code == -2011) {
                // 该订单挂单失效，这时候取消/查询不到订单，做重新查询
                url = generateSignedUrl("/fapi/v1/order", params);
                request = HttpRequest.newBuilder(new URI(url))
                        .GET()
                        .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                result = Utils.mapper.readValue(response.body(), BinanceOrder.class);

                if (result.code != 0) {
                    throw new ExApiException("failed to get order, resp: " + response.body());
                }
            }
            return new CurrentOrder(result.orderId, result.symbol,
                    OrderSide.valueOf(result.side),
                    OrderType.valueOf(result.type),
                    result.origQty, result.price, result.executedQty);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order " + symbol + ", id: " + orderId, e);
        }
    }

    @Override
    public RiskLimitValue getRiskLimitValue() throws ExApiException {
        try {
            String url = generateSignedUrl("/fapi/v2/account", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = Utils.mapper.readValue(response.body(), BinanceRestV1.class);

            if (result.code != 0) {
                throw new IOException("failed to get risk limit value: " + response.body());
            }

            List<PositionRiskLimitValue> positionRiskLimitValues = new ArrayList<>();
            for (BinancePositionData position : result.positions) {
                positionRiskLimitValues.add(new PositionRiskLimitValue(
                        position.symbol, (int) position.leverage,
                        (long) position.maxNotional, Math.round(position.leverage * position.initialMargin),
                        position.initialMargin, position.maintMargin));
            }

            return new RiskLimitValue(result.totalWalletBalance,
                    result.availableBalance, positionRiskLimitValues);
        } catch (Exception e) {
            throw new ExApiException("failed to get risk limit value", e);
        }
    }

    @Override
    public void updateRiskLimit(String symbol, int leverage) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("symbol", symbol);
            params.put("leverage", String.valueOf(leverage));
            String url = generateSignedUrl("/fapi/v1/leverage", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BinanceRestV1 result = reader.readValue(response.body());

            if (result.code != 0) {
                throw new IOException("failed to update leverage: " + result.msg);
            }
        } catch (Exception e) {
            throw new ExApiException("failed to update leverage", e);
        }
    }

    @Override
    public List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("incomeType", "FUNDING_FEE");
            params.put("symbol", symbol);
            params.put("limit", "100");
            params.put("startTime", String.valueOf(lastTime));
            params.put("endTime", String.valueOf(System.currentTimeMillis()));

            String url = generateSignedUrl("/fapi/v1/income", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BinanceAssetData> result = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            List<FundingValue> values = new ArrayList<>();
            for (BinanceAssetData data : result) {
                // 此时rate为空，需要重新获取
                params.clear();
                params.put("symbol", symbol);
                params.put("startTime", String.valueOf(data.time - 60000));
                params.put("limit", "1");
                params.put("endTime", String.valueOf(data.time + 60000));

                url = generateSignedUrl("/fapi/v1/fundingRate", params);
                request = HttpRequest.newBuilder(new URI(url))
                        .GET()
                        .header("X-MBX-APIKEY", accountConfig.getAuthKey())
                        .build();
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                List<BinanceAssetData> result1 = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

                if (result1.isEmpty()) {
                    throw new ExApiException("can not get funding rate, symbol: " +
                            symbol + ", start time: " + lastTime);
                }

                values.add(new FundingValue(data.symbol,
                        accountConfig.getName(), data.income, result1.get(0).fundingRate, data.time));
            }
            // 注意每次只获取一个symbol的funding value，为了防止触发频率限制，加入sleep
            Thread.sleep(200);
            return values;
        } catch (Exception e) {
            throw new ExApiException("failed to get funding value", e);
        }
    }

    private String generateUrl(String path, Map<String, String> params) {
        params.put("recvWindow", "5000");
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return accountConfig.getUrl() + path + "?" + HttpUtils.param2String(params);
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

    /**
     * json对象第一层
     */

    static class BinanceOrder {
        public int code;
        public String msg;

        public String orderId;
        public double origQty;
        public String type;
        public double price;
        public String side;
        public double executedQty;

        public String symbol;

        public BinanceOrder() {
        }
    }

}