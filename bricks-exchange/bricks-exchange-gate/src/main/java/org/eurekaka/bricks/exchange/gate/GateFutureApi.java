package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GateFutureApi implements FutureExApi {
    private static final String EMPTY_STRING_HASHED = "cf83e1357eefb8bdf1542850d66d8007d6" +
            "20e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e";
    private static final String BASE_PREFIX = "/api/v4/futures/usdt";

    private final static Logger logger = LoggerFactory.getLogger(GateFutureApi.class);

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;

    private final Map<String, Double> contractQuantos;

    public GateFutureApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;

        this.contractQuantos = new ConcurrentHashMap<>();

        try {
            getExchangeInfos();
        } catch (ExApiException e) {
            throw new InitializeException("failed to initialize gate future api", e);
        }
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        return null;
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        try {
            List<ExSymbol> symbols = new ArrayList<>();

            String url = accountConfig.getUrl() + BASE_PREFIX + "/contracts";
            HttpRequest request = HttpRequest.newBuilder(new URI(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            List<GateRespDataV1> result = resolveResponse(response);
            for (GateRespDataV1 res : result) {
                symbols.add(new ExSymbol(res.name,
                        Utils.roundPrecisionValue(res.order_price_round),
                        Utils.roundPrecisionValue(res.quanto_multiplier)));
                contractQuantos.put(res.name, res.quanto_multiplier);
            }

            return symbols;
        } catch (Exception t) {
            throw new ExApiException("failed to get exchange infos", t);
        }
    }

    public double getSize(String symbol, double size) {
        return contractQuantos.get(symbol) * size;
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        try {
            List<AccountValue> accountValues = new ArrayList<>();
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/accounts");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GateRespV1 result = Utils.mapper.readValue(response.body(), GateRespV1.class);
            accountValues.add(new AccountValue(result.currency, accountConfig.getName(),
                    result.total + result.unrealised_pnl, result.total, result.available));
            accountValues.add(new AccountValue("POINT", accountConfig.getName(), result.point, result.point));
            return accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }

    @Override
    public List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        try {
            List<PositionValue> positionValues = new ArrayList<>();
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/positions");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GateRespDataV1> result = resolveResponse(response);
            for (GateRespDataV1 res : result) {
                if ("single".equals(res.mode)) {
                    double size = getSize(res.contract, res.size);
                    positionValues.add(new PositionValue(res.contract,
                            accountConfig.getName(), size, res.mark_price,
                            Math.round(res.value), res.entry_price, res.unrealised_pnl));
                }
            }
            return positionValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get positions", e);
        }
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("contract", order.getSymbol());
            if (!contractQuantos.containsKey(order.getSymbol())) {
                throw new ExApiException("no contract quanto found for " + order.getSymbol());
            }
            long size = Math.round(order.getSize() / contractQuantos.get(order.getSymbol()));
            if (size == 0) {
                return OrderResultValue.FAIL_OK.name();
            }
            if (OrderSide.SELL.equals(order.getSide())) {
                size = -size;
            }
            data.put("size", String.valueOf(size));
            if (OrderType.MARKET.equals(order.getOrderType())) {
                data.put("price", "0");
                data.put("tif", "ioc");
            } else if (OrderType.LIMIT.equals(order.getOrderType())) {
                data.put("price", String.valueOf(order.getPrice()));
                data.put("tif", "poc");
            }
            data.put("iceberg", "0");
            data.put("text", "t-" + order.getId());
            String body = Utils.mapper.writeValueAsString(data);
            HttpRequest request = generateSignedRequest("POST", BASE_PREFIX + "/orders", body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GateRespV1 result = Utils.mapper.readValue(response.body(), GateRespV1.class);
            if (result.id == 0) {
                if ("ORDER_POC_IMMEDIATE".equals(result.label)) {
                    return OrderResultValue.FAIL_OK.name();
                }
                throw new ExApiException("unknown error response label: " + response.body());
            }
            return String.valueOf(result.id);
        } catch (Exception e) {
            throw new ExApiException("failed to make order", e);
        }
    }

    @Override
    public CompletableFuture<CurrentOrder> asyncMakeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> data = new HashMap<>();
            data.put("contract", order.getSymbol());
            if (!contractQuantos.containsKey(order.getSymbol())) {
                throw new ExApiException("no contract quanto found for " + order.getSymbol());
            }
            long size = Math.round(order.getSize() / contractQuantos.get(order.getSymbol()));
            if (size == 0) {
                throw new ExApiException("failed to make order, size is 0");
            }
            if (OrderSide.SELL.equals(order.getSide())) {
                size = -size;
            }
            data.put("size", String.valueOf(size));
            if (OrderType.MARKET.equals(order.getOrderType())) {
                data.put("price", "0");
                data.put("tif", "ioc");
            } else if (OrderType.LIMIT_GTX.equals(order.getOrderType())) {
                data.put("price", String.valueOf(order.getPrice()));
                data.put("tif", "poc");
            } else if (OrderType.LIMIT_IOC.equals(order.getOrderType())) {
                data.put("price", String.valueOf(order.getPrice()));
                data.put("tif", "ioc");
            } else if (OrderType.LIMIT_GTC.equals(order.getOrderType())) {
                data.put("price", String.valueOf(order.getPrice()));
                data.put("tif", "gtc");
            } else {
                throw new ExApiException("unsupported order type: " + order.getOrderType());
            }
            data.put("iceberg", "0");
            data.put("text", "t-" + order.getOrderId());
            String body = Utils.mapper.writeValueAsString(data);
            HttpRequest request = generateSignedRequest("POST", BASE_PREFIX + "/orders", body);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                try {
                    GateOrder result = Utils.mapper.readValue(response.body(), GateOrder.class);
//                    System.out.println(response.body());
                    if (result.id == 0) {
                        if ("ORDER_POC_IMMEDIATE".equals(result.label)) {
                            return null;
                        }
                        throw new CompletionException(
                                new ExApiException("failed to make order: " + response.body()));
                    }
                    double filled = (result.size - result.left) * contractQuantos.get(result.contract);
                    return new CurrentOrder(result.text, order.getName(), order.getSymbol(),
                            order.getSide(), order.getOrderType(), order.getSize(), order.getPrice(),
                            filled, GateUtils.getStatus(result.status, result.finish_as), result.finish_time);
                } catch (Exception e) {
                    throw new CompletionException("failed to parse response body: " + response.body(), e);
                }
            });
        } catch (Exception e) {
            throw new ExApiException("failed to make order", e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/orders?contract=" + symbol + "&status=open");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GateRespDataV1> result = resolveResponse(response);
            List<CurrentOrder> currentOrders = new ArrayList<>();
            for (GateRespDataV1 data : result) {
                double size = getRealSize(data.contract, data.size);
                double left = getRealSize(data.contract, data.left);
                OrderSide side = data.size < 0 ? OrderSide.SELL : OrderSide.BUY;
                currentOrders.add(new CurrentOrder(data.id, data.contract,
                        side, OrderType.LIMIT, size, data.price, size - left));
            }
            return currentOrders;
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CompletableFuture<CurrentOrder> asyncGetOrder(String symbol, String orderId) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/orders/t-" + orderId);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            GateOrder result = Utils.mapper.readValue(response.body(), GateOrder.class);
                            if (response.statusCode() != 200) {
                                if ("SERVER_ERROR".equals(result.label)) {
                                    return null;
                                }
                                throw new CompletionException(new ExApiException(
                                        "failed to get order: " + response.body()));
                            }

                            double size = getRealSize(result.contract, result.size);
                            double left = getRealSize(result.contract, result.left);
                            return new CurrentOrder(orderId, symbol,
                                    GateUtils.getOrderSide(result.size),
                                    GateUtils.getOrderType(result.price, result.tif),
                                    size, result.fill_price, size - left,
                                    GateUtils.getStatus(result.status, result.finish_as),
                                    result.finish_time);
                        } catch (JsonProcessingException e) {
                            throw new CompletionException("failed to parse response body", e);
                        }
                    });
        } catch (Exception e) {
            throw new ExApiException("failed to get order", e);
        }
    }

    @Override
    public CompletableFuture<List<CurrentOrder>> asyncGetCurrentOrders(String symbol) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/orders?contract=" + symbol + "&status=open");
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            throw new CompletionException(new ExApiException(
                                    "failed to get current orders:" + response.body()));
                        }
                        try {
                            List<GateOrder> orders = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
                            return orders.stream().map(result -> {
                                double size = getRealSize(result.contract, result.size);
                                double left = getRealSize(result.contract, result.left);
                                return new CurrentOrder(result.text.substring(2), symbol,
                                        GateUtils.getOrderSide(result.size),
                                        GateUtils.getOrderType(result.price, result.tif),
                                        size, result.fill_price, size - left,
                                        GateUtils.getStatus(result.status, result.finish_as),
                                        result.finish_time);
                            }).collect(Collectors.toList());
                        } catch (JsonProcessingException e) {
                            throw new CompletionException("failed to parse response body", e);
                        }
                    });
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("DELETE",
                    BASE_PREFIX + "/orders/" + orderId, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GateRespDataV1 result = Utils.mapper.readValue(response.body(), GateRespDataV1.class);
            if ("ORDER_NOT_FOUND".equals(result.label)) {
                request = generateSignedRequest(BASE_PREFIX + "/orders/" + orderId);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                result = Utils.mapper.readValue(response.body(), GateRespDataV1.class);
            }
            double size = getRealSize(result.contract, result.size);
            double left = getRealSize(result.contract, result.left);
            OrderSide side = result.size < 0 ? OrderSide.SELL : OrderSide.BUY;
            return new CurrentOrder(result.id, result.contract,
                    side, OrderType.LIMIT, size, result.price, size - left);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public CompletableFuture<Void> asyncCancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("DELETE",
                    BASE_PREFIX + "/orders/t-" + orderId, null);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            try {
                                GateRespV1 result = Utils.mapper.readValue(response.body(), GateRespV1.class);
                                if (!"ORDER_NOT_FOUND".equals(result.label)) {
                                    throw new CompletionException(new ExApiException("failed to cancel order: " + response.body()));
                                }
                            } catch (JsonProcessingException e) {
                                throw new CompletionException("failed to parse response body: " + response.body(), e);
                            }
                        }
                    });
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public RiskLimitValue getRiskLimitValue() throws ExApiException {
        try {
            List<PositionRiskLimitValue> positionRiskLimitValues = new ArrayList<>();
            HttpRequest request = generateSignedRequest(BASE_PREFIX + "/positions");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GateRespDataV1> result = resolveResponse(response);
            for (GateRespDataV1 res : result) {
                if ("single".equals(res.mode)) {
                    double sizeLimit = getSize(res.contract, res.risk_limit);
                    long limitValue = Math.round(res.mark_price * sizeLimit);
                    double initValue = Math.round(res.margin * 100) * 1.0 / 100;
                    double maintValue = Math.round(res.value * res.maintenance_rate * 100) * 1.0 / 100;

                    positionRiskLimitValues.add(new PositionRiskLimitValue(res.contract,
                            res.cross_leverage_limit, limitValue, Math.round(res.value),
                            initValue, maintValue));
                }
            }

            HttpRequest request2 = generateSignedRequest(BASE_PREFIX + "/accounts");
            HttpResponse<String> response2 = httpClient.send(request2, HttpResponse.BodyHandlers.ofString());
            GateRespV1 result2 = Utils.mapper.readValue(response2.body(), GateRespV1.class);

            return new RiskLimitValue(result2.total + result2.unrealised_pnl,
                    result2.available, positionRiskLimitValues);
        } catch (Exception e) {
            throw new ExApiException("failed to get risk limit value", e);
        }
    }

    @Override
    public void updateRiskLimit(String symbol, int leverage) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("POST",
                    BASE_PREFIX + "/positions/" + symbol + "/risk_limit?risk_limit=" + leverage, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            GateRespV1 result = Utils.mapper.readValue(response.body(), GateRespV1.class);

            if (result.label != null) {
                throw new ExApiException(response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to update risk_limit", e);
        }
    }

    @Override
    public List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException {
        try {
            long currentTime = System.currentTimeMillis();
            HttpRequest request = generateSignedRequest(BASE_PREFIX +
                    "/account_book?type=fund&limit=1000&from=" + lastTime / 1000 + "&to=" + currentTime / 1000);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<GateRespDataV1> result = resolveResponse(response);
            List<FundingValue> fundingValues = new ArrayList<>();
            for (GateRespDataV1 res : result) {
                // 获取历史资金费率
                String url = accountConfig.getUrl() + BASE_PREFIX +
                        "/funding_rate?contract=" + res.text + "&limit=1";
                HttpRequest request1 = HttpRequest.newBuilder(new URI(url)).GET().build();
                HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
                List<GateRespDataV1> result1 = resolveResponse(response1);
                double rate = 0;
                for (GateRespDataV1 res1 : result1) {
                    if (res1.t == res.time) {
                        rate = res1.r;
                    }
                }
                fundingValues.add(new FundingValue(res.text,
                        accountConfig.getName(), res.change, rate, res.time * 1000));
                Thread.sleep(100);
            }
            return fundingValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get funding values", e);
        }
    }


    private double getRealSize(String contract, long size) {
        if (size < 0) {
            return - size * contractQuantos.get(contract);
        } else {
            return size * contractQuantos.get(contract);
        }
    }

    private HttpRequest generateSignedRequest(String path) throws Exception {
        return generateSignedRequest("GET", path, null);
    }

    /**
     * 生成签名请求
     * @param method GET, POST, DELETE
     * @param body json string
     * @return http 请求
     */
    private HttpRequest generateSignedRequest(String method, String path,
                                              String body) throws Exception {
        long currentTime = System.currentTimeMillis() / 1000;

        String signString = method + "\n";
        String[] paths = path.split("\\?");
        if (paths.length == 1) {
            signString += paths[0] + "\n\n";
        } else if (paths.length == 2) {
            signString += paths[0] + "\n" + paths[1] + "\n";
        }

        if (body != null) {
            signString += Utils.encodeHexString(Utils.sha512(body));
        } else {
            signString += EMPTY_STRING_HASHED;
        }

        signString += "\n" + currentTime;

        Mac sha512Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA512");
        String signature = Utils.encodeHexString(sha512Mac.doFinal(signString.getBytes()));

        HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(accountConfig.getUrl() + path));
        builder.header("KEY", accountConfig.getAuthKey())
                .header("SIGN", signature)
                .header("Timestamp", String.valueOf(currentTime))
                .header("Content-Type", "application/json");

        if (body != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private List<GateRespDataV1> resolveResponse(HttpResponse<String> response) throws ExApiException {
        if (response.statusCode() >= 400) {
            throw new ExApiException("response status code error: " +
                    response.statusCode() + ", body: " + response.body());
        }
        try {
            return Utils.mapper.readValue(response.body(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new ExApiException("failed to parse response body: " + response.body());
        }
    }



    static class GateRespV1 {
        public String label;
        public String message;

        public double total;
        public String currency;
        public double point;
        public double unrealised_pnl;
        public double available;

        public long id;

        public GateRespV1() {
        }
    }

    static class GateRespDataV1 {
        public String label;

        public String name;
        public double quanto_multiplier;
        public double order_price_round;

        public String id;
        public String contract;
        // use left, instead of size
        public long size;
        public double price;
        public long left;

        public long risk_limit;
        public double maintenance_rate;
        public double value;
        public double margin;
        public double mark_price;
        public String mode;
        public int cross_leverage_limit;
        public double entry_price;
        public double unrealised_pnl;

        public long time;
        public double change;
        public String text;

        // funding rate
        public long t;
        public double r;

        public GateRespDataV1() {
        }
    }
}
