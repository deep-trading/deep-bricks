package org.eurekaka.bricks.exchange.bhex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BhexFutureApi implements FutureExApi {

    private final AccountConfig accountConfig;
    private final HttpClient httpClient;

    private final Map<String, Double> contractMultiplier;
    // 默认5倍杠杆不变
    private final int leverage;

    public BhexFutureApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.accountConfig = accountConfig;
        this.httpClient = httpClient;

        this.contractMultiplier = new ConcurrentHashMap<>();
        this.leverage = Integer.parseInt(accountConfig.getProperty("leverage", "5"));

        try {
            getExchangeInfos();
        } catch (ExApiException e) {
            throw new InitializeException("failed to initialize bhex contract infos", e);
        }
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        return null;
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        try {
            List<ExSymbol> infos = new ArrayList<>();
            HttpRequest request = HttpRequest.newBuilder(new URI(
                    accountConfig.getUrl() + "/openapi/v1/brokerInfo"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestRespV2 resp = Utils.mapper.readValue(response.body(), BhexRestRespV2.class);

            if (resp.code != 0) {
                throw new ExApiException("request error: " + response.body());
            }
            for (BhexRestDataV2 data : resp.contracts) {
                if (data.contractMultiplier > 0 && !data.inverse) {
                    double priceP = Utils.roundPrecisionValue(data.quoteAssetPrecision);
                    double sizeP = Utils.roundPrecisionValue(data.contractMultiplier);
                    infos.add(new ExSymbol(data.symbol, priceP, sizeP));
                    contractMultiplier.put(data.symbol, sizeP);
                }
            }

            return infos;
        } catch (Exception e) {
            throw new ExApiException("failed to get exchange info", e);
        }
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        try {
            List<AccountValue> accountValues = new ArrayList<>();
            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/contract/v1/account", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, BhexRestDataV2> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            for (BhexRestDataV2 value : resp.values()) {
                accountValues.add(new AccountValue(value.tokenId, accountConfig.getName(),
                        value.total, value.positionMargin, value.availableMargin));
            }

            return accountValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }


    @Override
    public List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        Map<String, BhexPosValue> positionValues = getBhexPositions();

        Set<String> symbols = positionValues.values().stream()
                .map(e -> e.symbol)
                .filter(e -> symbol == null || symbol.equals(e))
                .collect(Collectors.toSet());

        List<PositionValue> values = new ArrayList<>();
        for (String sym : symbols) {
            BhexPosValue longPos = positionValues.get(sym + "LONG");
            BhexPosValue shortPos = positionValues.get(sym + "SHORT");
            double size = 0;
            long quantity = 0;
            long margin = 0;
            double price = 0;
            if (longPos != null) {
                size += longPos.size;
                quantity += longPos.positionValue;
                margin += longPos.margin;
                price = longPos.lastPrice;
            }
            if (shortPos != null) {
                size -= shortPos.size;
                quantity -= shortPos.positionValue;
                margin -= shortPos.margin;
                price = shortPos.lastPrice;
            }
            values.add(new PositionValue(sym, accountConfig.getName(), size, price, quantity, 0, 0));
        }
        return values;
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("clientOrderId", order.getSymbol() + System.nanoTime());
            params.put("symbol", order.getSymbol());
            if (order.getSide().equals(OrderSide.BUY)) {
                params.put("side", "BUY_OPEN");
            } else if (order.getSide().equals(OrderSide.BUY_SHORT)) {
                params.put("side", "BUY_CLOSE");
            } else if (order.getSide().equals(OrderSide.SELL)) {
                params.put("side", "SELL_OPEN");
            } else if (order.getSide().equals(OrderSide.SELL_LONG)) {
                params.put("side", "SELL_CLOSE");
            }
            params.put("orderType", "LIMIT");
            if (order.getOrderType().equals(OrderType.LIMIT)) {
                params.put("priceType", "INPUT");
                params.put("timeInForce", "GTC");
                params.put("price", String.valueOf(order.getPrice()));
            } else if (order.getOrderType().equals(OrderType.LIMIT_IOC)) {
                params.put("priceType", "INPUT");
                params.put("timeInForce", "IOC");
                params.put("price", String.valueOf(order.getPrice()));
            } else if (order.getOrderType().equals(OrderType.MARKET)) {
                params.put("priceType", "MARKET");
                params.put("timeInForce", "GTC");
            }

            double size = order.getSize() * contractMultiplier.get(order.getSymbol());
            if (size == 0) {
                return OrderResultValue.FAIL_OK.name();
            }
            params.put("quantity", String.format("%f", size));

            params.put("leverage", String.valueOf(leverage));

            String url = BhexUtils.generateSignedUrl(accountConfig, "/openapi/contract/v1/order", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestRespV2 result = Utils.mapper.readValue(response.body(), BhexRestRespV2.class);

            if (result.code != 0 || result.orderId == null) {
                throw new IOException(response.body());
            }
            return result.orderId;
        } catch (Exception e) {
            throw new ExApiException("failed to make order.", e);
        }
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        try {
            List<CurrentOrder> currentOrders = new ArrayList<>();
            Map<String, String> params = new HashMap<>();
            params.put("orderType", "LIMIT");
            params.put("symbol", symbol);
            params.put("limit", String.valueOf(100));

            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/contract/v1/openOrders", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BhexRestDataV2> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            for (BhexRestDataV2 value : resp) {
                OrderSide side = OrderSide.BUY;
                if (value.side.startsWith("SELL")) {
                    side = OrderSide.SELL;
                }
                if (type == 0 || type == 1 && OrderSide.BUY.equals(side) ||
                        type == 2 && OrderSide.SELL.equals(side)) {
                    double size = getSize(value.symbol, value.origQty);
                    double filledSize = getSize(value.symbol, value.executedQty);

                    currentOrders.add(new CurrentOrder(value.orderId, value.symbol,
                            side, OrderType.LIMIT, size, value.price, filledSize));
                }
            }

            return currentOrders;
        } catch (Exception e) {
            throw new ExApiException("failed to get current orders", e);
        }
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("orderType", "LIMIT");
            params.put("orderId", orderId);

            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/contract/v1/order/cancel", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .DELETE()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestDataV2 value = Utils.mapper.readValue(response.body(), BhexRestDataV2.class);

            if (value.code != 0 && value.code != -1139) {
                throw new ExApiException(response.body());
            }
            if (value.code == -1139) {
                // 订单已经成交
                String url1 = BhexUtils.generateSignedUrl(accountConfig,
                        "/openapi/contract/v1/getOrder", params);
                HttpRequest request1 = HttpRequest.newBuilder(new URI(url1))
                        .GET()
                        .header("X-BH-APIKEY", accountConfig.getAuthKey())
                        .build();
                HttpResponse<String> response1 = httpClient.send(request1, HttpResponse.BodyHandlers.ofString());
                value = Utils.mapper.readValue(response.body(), BhexRestDataV2.class);
            }

            OrderSide side = OrderSide.BUY;
            if (value.side.startsWith("SELL")) {
                side = OrderSide.SELL;
            }
            double size = getSize(value.symbol, value.origQty);
            double filledSize = getSize(value.symbol, value.executedQty);

            return new CurrentOrder(value.orderId, value.symbol,
                    side, OrderType.LIMIT, size, value.price, filledSize);
        } catch (Exception e) {
            throw new ExApiException("failed to cancel order", e);
        }
    }

    @Override
    public RiskLimitValue getRiskLimitValue() throws ExApiException {
        try {
            List<PositionRiskLimitValue> positionRiskLimitValues = new ArrayList<>();
            Map<String, BhexPosValue> positionValues = getBhexPositions();

            Set<String> symbols = positionValues.values().stream()
                    .map(e -> e.symbol).collect(Collectors.toSet());

            for (String symbol : symbols) {
                BhexPosValue longPos = positionValues.get(symbol + "LONG");
                BhexPosValue shortPos = positionValues.get(symbol + "SHORT");
                long quantity = 0;
                long margin = 0;
                if (longPos != null) {
                    quantity += longPos.positionValue;
                    margin += longPos.margin;
                }
                if (shortPos != null) {
                    quantity -= shortPos.positionValue;
                    margin -= shortPos.margin;
                }
                positionRiskLimitValues.add(new PositionRiskLimitValue(symbol,
                        leverage, 0, quantity, 0, margin));
            }

            AccountValue usdtValue = null;
            for (AccountValue value : getAccountValue()) {
                if ("USDT".equals(value.asset)) {
                    usdtValue = value;
                }
            }
            if (usdtValue == null) {
                throw new ExApiException("no usdt account value");
            }
            return new RiskLimitValue(usdtValue.totalBalance,
                    usdtValue.availableBalance, positionRiskLimitValues);
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }

    public Map<String, BhexPosValue> getBhexPositions() throws ExApiException {
        try {
            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/contract/v1/positions", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .GET()
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            List<BhexRestDataV2> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});

            Map<String, BhexPosValue> positionValues = new HashMap<>();
            for (BhexRestDataV2 data : resp) {
                double size = getSize(data.symbol, data.position);
                double availSize = getSize(data.symbol, data.available);
                positionValues.put(data.symbol + data.side,
                        new BhexPosValue(data.symbol, data.side, size, availSize,
                                data.lastPrice, Math.round(data.positionValue), Math.round(data.margin)));
            }
            return positionValues;
        } catch (Exception e) {
            throw new ExApiException("failed to get account value", e);
        }
    }


    @Override
    public void updateRiskLimit(String symbol, int leverage) throws ExApiException {
        // 不操作
    }

    @Override
    public List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException {
        return Collections.emptyList();
    }

    public double getSize(String symbol, double position) throws ExApiException {
        if (!contractMultiplier.containsKey(symbol)) {
            getExchangeInfos();
        }
        return position / contractMultiplier.get(symbol);
    }

    public String getListenKey() throws ExApiException {
        try {
            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/v1/userDataStream", new HashMap<>());
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestDataV2 resp = Utils.mapper.readValue(response.body(), BhexRestDataV2.class);

            if (resp.code != 0 || resp.listenKey == null) {
                throw new ExApiException(response.body());
            }
            return resp.listenKey;
        } catch (Exception e) {
            throw new ExApiException("failed to get listen key", e);
        }
    }

    public void keepListenKey(String listenKey) throws ExApiException {
        try {
            Map<String, String> params = new HashMap<>();
            params.put("listenKey", listenKey);
            String url = BhexUtils.generateSignedUrl(accountConfig,
                    "/openapi/v1/userDataStream", params);
            HttpRequest request = HttpRequest.newBuilder(new URI(url))
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .header("X-BH-APIKEY", accountConfig.getAuthKey())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BhexRestDataV2 resp = Utils.mapper.readValue(response.body(), BhexRestDataV2.class);

            if (resp.code != 0) {
                throw new ExApiException(response.body());
            }
        } catch (Exception e) {
            throw new ExApiException("failed to get listen key", e);
        }
    }


    static class BhexRestRespV2 {
        public int code;
        public String msg;

        public List<BhexRestDataV2> contracts;

        public String orderId;
    }

    static class BhexRestDataV2 {
        public int code;
        public String msg;

        public String listenKey;

        public double total;
        public double availableMargin;
        public double positionMargin;
        public double orderMargin;
        public String tokenId;

        public String symbol;
        public double quoteAssetPrecision;
        public double contractMultiplier;
        public boolean inverse;

        public String side;
        public double position;
        public double available;
        public double lastPrice;
        public double positionValue;
        public double margin;

        public long time;
        public String orderId;
        public double price;
        public double origQty;
        public String orderType;
        public double executedQty;
    }

}
