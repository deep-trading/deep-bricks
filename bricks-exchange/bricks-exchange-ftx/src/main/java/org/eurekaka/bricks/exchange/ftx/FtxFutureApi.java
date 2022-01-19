package org.eurekaka.bricks.exchange.ftx;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class FtxFutureApi implements FutureExApi {
    private final static Logger logger = LoggerFactory.getLogger(FtxFutureApi.class);

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;
    private final String subAccount;
    private final ObjectReader reader;
    private final ObjectReader reader2;
    private final Duration timeout;

    public FtxFutureApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;

        this.subAccount = accountConfig.getUid() == null ? null :
                URLEncoder.encode(accountConfig.getUid(), StandardCharsets.UTF_8);

        reader = Utils.mapper.reader().forType(FtxRestResult.class);
        reader2 = Utils.mapper.reader().forType(new TypeReference<List<FtxRestResult>>() {});
        this.timeout = Duration.ofMillis(Integer.parseInt(
                accountConfig.getProperty("http_request_timeout", "1500")));
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        Map<String, Object> login = new HashMap<>();
        login.put("op", "login");

        Map<String, Object> args = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        args.put("key", accountConfig.getAuthKey());

        String signString = currentTime + "websocket_login";
        Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
        String signature = Utils.encodeHexString(sha256Mac.doFinal(signString.getBytes()));

        args.put("sign", signature);
        args.put("time", currentTime);
        if (subAccount != null) {
            args.put("subaccount", subAccount);
        }
        login.put("args", args);

        try {
            return Utils.mapper.writeValueAsString(login);
        } catch (JsonProcessingException e) {
            throw new ExApiException("failed to generate auth message", e);
        }
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        try {
            List<ExSymbol> exSymbols = new ArrayList<>();
            URI uri = new URI(accountConfig.getUrl() + "/api/futures");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get exchange infos. " + response.body());
            }

            for (FtxRestResult res : result.result) {
                exSymbols.add(new ExSymbol(res.name,
                        Utils.roundPrecisionValue(res.priceIncrement),
                        Utils.roundPrecisionValue(res.sizeIncrement)));
            }

            return exSymbols;
        } catch (Exception e) {
            throw new ExApiException("failed to get exchange infos", e);
        }
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        try {
            List<AccountValue> accountValues = new ArrayList<>();

            HttpRequest request = generateSignedRequest("/api/wallet/balances");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get wallet balances. " + response.body());
            }
            for (FtxRestResult res : result.result) {
//                System.out.println("coin: " + res.coin + ", free: " + res.free + ", total: " + res.total);
                // 此处无法计算获取 unrealized pnl
                accountValues.add(new AccountValue(res.coin,
                        accountConfig.getName(), res.total, res.total, res.free));
            }

//            request = generateSignedRequest("/api/account");
//            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//            FtxRestV2 result2 = Utils.mapper.readValue(response.body(), FtxRestV2.class);
//            if (!result2.success || result2.result == null || result2.result.positions == null) {
//                throw new ExApiException("failed to get account. " + response.body());
//            }
//            // account 均由USD计价
//            System.out.println("collateral: " + result2.result.collateral);
//            System.out.println("freeCollateral: " + result2.result.freeCollateral);
//            System.out.println("totalAccountValue: " + result2.result.totalAccountValue);
//
//            double sum1 = 0;
//            double sum2 = 0;
//            double sum3 = 0;
//            for (FtxRestResultPos position : result2.result.positions) {
//                sum1 += position.unrealizedPnl;
//                sum2 += position.realizedPnl;
//                sum3 += position.collateralUsed;
//                double markPrice = position.collateralUsed / 0.1 / position.openSize;
//                System.out.println("future: " + position.future +
//                        ", collateralUsed: " + position.collateralUsed +
//                        ", unrealizedPnl: " + position.unrealizedPnl +
//                        ", realizedPnl: " + position.realizedPnl +
//                        ", markPrice: " + markPrice +
//                        ", my unrealized pnl: " + position.size * (markPrice - position.entryPrice));
//            }
//            System.out.println("total unrealizedPnl: " + sum1);
//            System.out.println("total realizedPnl: " + sum2);
//            System.out.println("total collateralUsed: " + sum3);

            return  accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account info", e);
        }
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("side", order.getSide().name().toLowerCase());
            params.put("market", order.getSymbol());
            params.put("type", order.getOrderType().name().toLowerCase());
            params.put("size", order.getSize());
            if (OrderType.MARKET.equals(order.getOrderType())) {
                params.put("price", null);
            } else if (OrderType.LIMIT.equals(order.getOrderType())) {
                params.put("price", order.getPrice());
                params.put("postOnly", true);
            }

            String body = Utils.mapper.writeValueAsString(params);

            HttpRequest request = generateSignedRequest("POST", "/api/orders", body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success || result.result == null) {
                if (result.error.startsWith("Size too small")) {
                    return OrderResultValue.FAIL_OK.name();
                }
                throw new ExApiException("failed to make order." + response.body());
            }

            if ("new".equals(result.result.status) || "open".equals(result.result.status)) {
                return result.result.id;
            } else {
                throw new ExApiException("failed to make order, resp: " + response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to make an order: " + order, e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("/api/orders?market=" + symbol);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get current orders." + response.body());
            }

            List<CurrentOrder> orders = new ArrayList<>();
            for (FtxRestResult res : result.result) {
                if (type == 0 ||
                        type == 1 && "buy".equals(res.side) ||
                        type == 2 && "sell".equals(res.side)) {
                    orders.add(new CurrentOrder(res.id, null, res.future,
                            OrderSide.valueOf(res.side.toUpperCase()),
                            OrderType.valueOf(res.type.toUpperCase()),
                            res.size, res.price, res.filledSize));
                }
            }
            return orders;
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            // 取消订单
            HttpRequest request = generateSignedRequest(
                    "DELETE", "/api/orders/" + orderId, null);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV3 result1 = Utils.mapper.readValue(response.body(), FtxRestV3.class);
            if (!result1.success && !"Order already closed".equals(result1.error) &&
                    !"Order already queued for cancellation".equals(result1.error)) {
                throw new ExApiException("failed to cancel order. resp: " + response.body());
            }

            // 查询订单状态
            request = generateSignedRequest("/api/orders/" + orderId);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result2 = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result2.success || result2.result == null) {
                throw new ExApiException("failed to get success resp: " + response.body());
            }
            FtxRestResult res = result2.result;
            return new CurrentOrder(res.id, null, res.future,
                    OrderSide.valueOf(res.side.toUpperCase()),
                    OrderType.valueOf(res.type.toUpperCase()),
                    res.size, res.price, res.filledSize);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public RiskLimitValue getRiskLimitValue() throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("/api/account");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get risk limit account. " + response.body());
            }

            List<PositionRiskLimitValue> positionRiskLimitValues = new ArrayList<>();
            for (FtxRestResultPos position : result.result.positions) {
                long pos = 0;
                if (position.initialMarginRequirement > 0) {
                    pos = Math.round(position.collateralUsed / position.initialMarginRequirement);
                }

                long initialMargin = Math.round(position.collateralUsed);
                long maintMargin = Math.round(pos * position.maintenanceMarginRequirement);

                positionRiskLimitValues.add(new PositionRiskLimitValue(
                        position.future, result.result.leverage, 0L, pos,
                        initialMargin, maintMargin));
            }

            return new RiskLimitValue(result.result.collateral,
                    result.result.freeCollateral, positionRiskLimitValues);
        } catch (Exception e) {
            throw new ExApiException("failed to get risk limit values", e);
        }
    }

    @Override
    public void updateRiskLimit(String symbol, int leverage) throws ExApiException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("leverage", leverage);
            String body = Utils.mapper.writeValueAsString(params);
            HttpRequest request = generateSignedRequest("POST", "/api/account/leverage", body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success) {
                throw new ExApiException("failed to update leverage." + response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to update account leverage.", e);
        }
    }

    @Override
    public List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("/api/funding_payments?start_time=" + lastTime / 1000);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get funding payments." + response.body());
            }

            List<FundingValue> values = new ArrayList<>();
            for (FtxRestResult res : result.result) {
                values.add(new FundingValue(res.future, accountConfig.getName(),
                        -res.payment, res.rate, FtxUtils.parseTimestampString(res.time)));
            }

            return values;
        } catch (Exception e) {
            throw new ExApiException("failed to get funding values", e);
        }
    }

    @Override
    public double getFundingRate(String symbol) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("/api/futures/" + symbol + "/stats");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get funding stats." + response.body());
            }
            return result.result.nextFundingRate;
        } catch (Exception e) {
            throw new ExApiException("failed to get funding rates", e);
        }
    }

    @Override
    public List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        try {
            List<PositionValue> positions = new ArrayList<>();

            HttpRequest request = generateSignedRequest("/api/positions?showAvgPrice=true");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException("failed to get positions." + response.body());
            }

            for (FtxRestResult res : result.result) {
                long quantity = Math.round(res.netSize * res.entryPrice);
                positions.add(new PositionValue(res.future, accountConfig.getName(),
                        res.netSize, res.entryPrice, quantity,
                        res.recentBreakEvenPrice, res.recentPnl));
            }

            return positions;
        } catch (Exception e) {
            throw new ExApiException("failed to get positions", e);
        }
    }

    @Override
    public CompletableFuture<CurrentOrder> asyncMakeOrder(Order order) throws ExApiException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("side", order.getSide().name().toLowerCase());
            params.put("market", order.getSymbol());
            params.put("size", order.getSize());
            params.put("clientId", order.getClientOrderId());

            if (OrderType.MARKET.equals(order.getOrderType())) {
                params.put("price", null);
                params.put("type", "market");
            } else {
                params.put("price", order.getPrice());
                params.put("type", "limit");
                if (OrderType.LIMIT_GTX.equals(order.getOrderType())) {
                    params.put("postOnly", true);
                } else if (OrderType.LIMIT_IOC.equals(order.getOrderType())) {
                    params.put("ioc", true);
                }
            }
            HttpRequest request = generateSignedRequest("POST", "/api/orders",
                    Utils.mapper.writeValueAsString(params));
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                if (response.statusCode() != 200) {
                    logger.error("failed to make order: {}", response.body());
                    return null;
                }
                try {
                    FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                    if (!resp.success || resp.result == null) {
                        logger.error("failed to make order, not success: {}", response.body());
                        return null;
                    }
                    FtxRestResult result = reader.readValue(resp.result);
                    return new CurrentOrder(result.id, order.getName(), order.getSymbol(), order.getAccount(),
                            order.getSide(), order.getOrderType(), order.getSize(), order.getPrice(), result.filledSize,
                            FtxUtils.getOrderStatus(result.status, result.size, result.filledSize),
                            System.currentTimeMillis(), result.clientId);
                } catch (Exception e) {
                    throw new CompletionException("failed to parse response body: " + response.body(), e);
                }
            }).exceptionally(ex -> {
                logger.error("failed to async make order, http request error", ex);
                return null;
            });
        } catch (Exception e) {
            throw new ExApiException("failed to make an order: " + order, e);
        }
    }

    @Override
    public CompletableFuture<CurrentOrder> asyncGetOrder(String symbol, String clientOrderId) throws ExApiException {
        try {
            // 查询订单状态
            HttpRequest request = generateSignedRequest("/api/orders/by_client_id/" + clientOrderId);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                try {
                    FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                    if (!resp.success || resp.result == null) {
                        logger.error("failed to get order: {}", response.body());
                        return null;
                    }
                    FtxRestResult result = reader.readValue(resp.result);
                    return new CurrentOrder(result.id, null, result.future, accountConfig.getName(),
                            FtxUtils.getOrderSide(result.side),
                            FtxUtils.getOrderType(result.type, result.ioc, result.postOnly),
                            result.size, result.price, result.filledSize,
                            FtxUtils.getOrderStatus(result.status, result.size, result.filledSize),
                            FtxUtils.parseTimestampString(result.createdAt), result.clientId);
                } catch (Exception e) {
                    throw new CompletionException("failed to parse response body: " + response.body(), e);
                }
            });
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public CompletableFuture<Boolean> asyncCancelOrder(String symbol, String clientOrderId) throws ExApiException {
        try {
            // 查询订单状态
            HttpRequest request = generateSignedRequest("DELETE",
                    "/api/orders/by_client_id/" + clientOrderId, null);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                try {
//                    System.out.println(response.statusCode() + ", " + response.body());
                    if (response.statusCode() != 200) {
                        FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                        if (!resp.success || resp.result == null) {
                            if ("Order already closed".equals(resp.error) ||
                                    "Order not found".equals(resp.error) ||
                                    "Order already queued for cancellation".equals(resp.error)) {
                                return true;
                            }
                        }
                        logger.info("failed to cancel order, symbol: {}, client order id: {}, response: {}",
                                symbol, clientOrderId, response.body());
                        return false;
                    }
                    return true;
                } catch (Exception e) {
                    throw new CompletionException("failed to parse response body: " + response.body(), e);
                }
            }).exceptionally(ex -> {
                logger.error("failed to async cancell order, http request error", ex);
                return false;
            });
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public CompletableFuture<List<CurrentOrder>> asyncGetCurrentOrders(String symbol) throws ExApiException {
        try {
            HttpRequest request = generateSignedRequest("/api/orders?market=" + symbol);
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                try {
                    FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                    if (!resp.success || resp.result == null) {
                        logger.error("failed to get current orders: {}", response.body());
                        return null;
                    }
                    List<FtxRestResult> results = reader2.readValue(resp.result);
                    List<CurrentOrder> orders = new ArrayList<>();
                    for (FtxRestResult result : results) {
                        orders.add(new CurrentOrder(result.id, null, result.future, accountConfig.getName(),
                                FtxUtils.getOrderSide(result.side),
                                FtxUtils.getOrderType(result.type, result.ioc, result.postOnly),
                                result.size, result.price, result.filledSize,
                                FtxUtils.getOrderStatus(result.status, result.size, result.filledSize),
                                FtxUtils.parseTimestampString(result.createdAt), result.clientId));
                    }
                    return orders;
                } catch (Exception e) {
                    throw new CompletionException("failed to parse response body: " + response.body(), e);
                }
            });
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CompletableFuture<List<AccountValue>> asyncGetAccountValues() throws ExApiException {
        HttpRequest request = generateSignedRequest("/api/wallet/balances");
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            try {
                FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                if (!resp.success || resp.result == null) {
                    logger.error("failed to get account values: {}", response.body());
                    return Collections.emptyList();
                }
                List<FtxRestResult> results = reader2.readValue(resp.result);
                List<AccountValue> accountValues = new ArrayList<>();
                for (FtxRestResult res : results) {
//                System.out.println("coin: " + res.coin + ", free: " + res.free + ", total: " + res.total);
                    // 此处无法计算获取 unrealized pnl
                    accountValues.add(new AccountValue(res.coin,
                            accountConfig.getName(), res.total, res.total, res.free));
                }
                return accountValues;
            } catch (Exception e) {
                throw new CompletionException("failed to parse response body: " + response.body(), e);
            }
        });
    }

    @Override
    public CompletableFuture<List<PositionValue>> asyncGetPositionValues() throws ExApiException {
        HttpRequest request = generateSignedRequest("/api/positions?showAvgPrice=true");
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            try {
                FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                if (!resp.success || resp.result == null) {
                    logger.error("failed to get current positions: {}", response.body());
                    return Collections.emptyList();
                }
                List<FtxRestResult> results = reader2.readValue(resp.result);
                List<PositionValue> positions = new ArrayList<>();
                for (FtxRestResult res : results) {
                    long quantity = Math.round(res.netSize * res.entryPrice);
                    positions.add(new PositionValue(res.future, accountConfig.getName(),
                            res.netSize, res.entryPrice, quantity,
                            res.recentBreakEvenPrice, res.recentPnl));
                }
                return positions;
            } catch (Exception e) {
                throw new CompletionException("failed to parse response body: " + response.body(), e);
            }
        });
    }

    @Override
    public CompletableFuture<Double> asyncGetFundingRate(String symbol) throws ExApiException {
        HttpRequest request = generateSignedRequest("/api/futures/" + symbol + "/stats");
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            try {
                FtxRestResp resp = Utils.mapper.readValue(response.body(), FtxRestResp.class);
                if (!resp.success || resp.result == null) {
                    logger.error("failed to get funding rate: {}", response.body());
                    return 0D;
                }
                FtxRestResult result = reader.readValue(resp.result);
                return result.nextFundingRate;
            } catch (Exception e) {
                throw new CompletionException("failed to parse response body: " + response.body(), e);
            }
        });
    }

    /**
     * 生成签名后的请求
     * @param path 请求路径，可以此处拼接参数
     * @return 签名后的请求
     * @throws ExApiException 生成失败
     */
    private HttpRequest generateSignedRequest(String path) throws ExApiException {
        return generateSignedRequest("GET", path, null);
    }

    /**
     * 生成签名请求
     * @param method GET, POST, DELETE
     * @param body json string
     * @return http 请求
     */
    private HttpRequest generateSignedRequest(String method, String path,
                                              String body) throws ExApiException {
        try {
            long currentTime = System.currentTimeMillis();
            String signString = currentTime + method + path;
            if (body != null) {
                signString += body;
            }
            Mac sha256Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA256");
            String signature = Utils.encodeHexString(sha256Mac.doFinal(signString.getBytes()));

            HttpRequest.Builder builder = HttpRequest.newBuilder(new URI(accountConfig.getUrl() + path));
            builder.header("FTX-KEY", accountConfig.getAuthKey())
                    .header("FTX-SIGN", signature)
                    .header("FTX-TS", String.valueOf(currentTime))
                    .header("Content-Type", "application/json");
            if (this.subAccount != null) {
                builder.header("FTX-SUBACCOUNT", this.subAccount);
            }
            if (body != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            builder.timeout(timeout);
            return builder.build();
        } catch (Exception e) {
            throw new ExApiException("failed to generate signed request: " + path, e);
        }
    }

    @Override
    public void transferAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("coin", transfer.asset);
            params.put("size", transfer.amount);
            params.put("source", transfer.fromAccountConfig.getUid());
            params.put("destination", transfer.toAccountConfig.getUid());

            String body = Utils.mapper.writeValueAsString(params);
            HttpRequest request = generateSignedRequest("POST", "/api/subaccounts/transfer", body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success) {
                throw new ExApiException(response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to transfer asset.", e);
        }
    }

    @Override
    public void withdrawAsset(AssetTransfer transfer) throws ExApiException {
        try {
            Map<String, Object> params = new HashMap<>();

            String address = transfer.toAccountConfig.getProperty("address");
            String password = transfer.fromAccountConfig.getProperty("pass");
            if (password != null) {
                params.put("password", password);
            }
            String code = transfer.fromAccountConfig.getProperty("code");
            if (code != null) {
                params.put("code", code);
            }

            params.put("coin", transfer.asset);
            params.put("size", transfer.amount);
            params.put("address", address);

            String body = Utils.mapper.writeValueAsString(params);
            HttpRequest request = generateSignedRequest("POST", "/api/wallet/withdrawals", body);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV2 result = Utils.mapper.readValue(response.body(), FtxRestV2.class);
            if (!result.success) {
                throw new ExApiException(response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to withdraw asset.", e);
        }
    }

    @Override
    public List<AccountAssetRecord> getAssetRecords(AssetTransferHistory transferHistory) throws ExApiException {
        try {
            List<AccountAssetRecord> records = new ArrayList<>();
            String path = transferHistory.type == 1 ? "/api/wallet/deposits" : "/api/wallet/withdrawals";
            path += "?start_time=" + transferHistory.start / 1000 + "&end_time=" +
                    transferHistory.stop / 1000 + "&limit=" + transferHistory.limit;

            HttpRequest request = generateSignedRequest(path);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            FtxRestV1 result = Utils.mapper.readValue(response.body(), FtxRestV1.class);
            if (!result.success || result.result == null) {
                throw new ExApiException(response.body());
            }
            for (FtxRestResult res : result.result) {
                records.add(new AccountAssetRecord(FtxUtils.parseTimestampString(res.time),
                        res.coin, res.size, res.status, null));
            }
            return records;
        } catch (Exception e) {
            throw new ExApiException("failed to get asset records", e);
        }
    }

    // 所有返回的请求都需监测 success = true
    static class FtxRestV1 {
        public boolean success;
        public String error;

        public List<FtxRestResult> result;

        public FtxRestV1() {
        }
    }

    static class FtxRestV2 {
        public boolean success;
        public String error;

        public FtxRestResult result;

        public FtxRestV2() {
        }
    }

    static class FtxRestV3 {
        public boolean success;
        public String error;

        public String result;

        public FtxRestV3() {
        }
    }

    static class FtxRestResultPos {
        public double cost;
        public double unrealizedPnl;
        public double netSize;
        public double size;
        public double realizedPnl;
        public String future;
        public double collateralUsed;
        public double openSize;
        public double entryPrice;

        public double initialMarginRequirement;
        public double maintenanceMarginRequirement;

        public FtxRestResultPos() {
        }
    }

}
