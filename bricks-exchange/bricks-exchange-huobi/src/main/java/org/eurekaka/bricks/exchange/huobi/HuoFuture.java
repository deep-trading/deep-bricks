package org.eurekaka.bricks.exchange.huobi;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

/**
 * huobi websocket，需要支持三个websocket地址连接，坑爹
 * 默认实现使用
 */
public class HuoFuture extends AbstractFutureExchange {
    // 行情地址
    private final static String MARKET_WEBSOCKET = "wss://api.hbdm.com/linear-swap-ws";

    private final static String POSITION_BUY = "buy";
    private final static String POSITION_SELL = "sell";

    // 行情websocket
    private WebSocket marketWebSocket;
    private final WebSocket.Listener marketListener;

    public HuoFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());

        this.marketListener = new HuoFutureMarketListener(accountConfig, accountStatus, (HuoFutureApi) api);
    }

    @Override
    public void stop() {
        super.stop();
        // 暂停market websocket
        this.marketWebSocket.abort();
    }

    @Override
    protected void postStart() throws ExchangeException {
        startIndexMarketWebsockets();
        try {
            List<PositionValue> positionValues = api.getPositionValue(null);
            for (PositionValue positionValue : positionValues) {
                accountStatus.getPositionValues().put(positionValue.getName(), positionValue);
            }
        } catch (ExApiException e) {
            throw new ExchangeException("failed to initialize huo future position", e);
        }
    }

    private void startIndexMarketWebsockets() throws ExchangeException {
        int httpConnectTimeout = Integer.parseInt(
                accountConfig.getProperty("http_connect_timeout", "10"));

        // 启动market websocket
        try {
            String marketUrl = accountConfig.getProperty("http_market_websocket", MARKET_WEBSOCKET);
            this.marketWebSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(httpConnectTimeout))
                    .buildAsync(new URI(marketUrl), marketListener)
                    .get(httpConnectTimeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ExchangeException("failed to create websocket", e);
        }
    }

    @Override
    protected void sendSub(String symbol) throws ExApiException {
        try {
            marketWebSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV1("market." + symbol + ".depth.step6")), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("sub", "orders_cross." + symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("sub", "positions_cross." + symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("sub", "public." + symbol + ".funding_rate")), true);
            // 1s 最多30个订阅请求
            Thread.sleep(150);
        } catch (Exception e) {
            throw new ExApiException("failed to send subscription for symbol: " + symbol, e);
        }
    }

    @Override
    protected void sendUnsub(String symbol) throws ExApiException {
        try {
            marketWebSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV1("market." + symbol + ".depth.step6")), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("unsub", "orders_cross." + symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("unsub", "positions_cross." + symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new HuoFutureSubV2("unsub", "public." + symbol + ".funding_rate")), true);
        } catch (JsonProcessingException e) {
            throw new ExApiException("failed to send unsubscription for symbol: " + symbol, e);
        }
    }

    @Override
    protected ExMessage<RiskLimitValue> getRiskLimit() throws ExApiException {
        RiskLimitValue riskLimitValue = api.getRiskLimitValue();
        List<PositionRiskLimitValue> availablePositions = new ArrayList<>();
        for (PositionRiskLimitValue value : riskLimitValue.positionRiskLimitValues) {
            if (accountStatus.getSymbols().containsKey(value.symbol) &&
                    accountStatus.getNetValues().containsKey(value.symbol)) {

                long price = accountStatus.getNetValues().get(value.symbol).getPrice();
                double limit = ((HuoFutureApi) api).getSize(value.symbol, value.limitValue);
                long posLimit = Math.round(price * limit / PRECISION);

                PositionRiskLimitValue posValue = new PositionRiskLimitValue(value.symbol,
                        value.leverage, posLimit, value.position, value.initValue, value.maintainValue);
                posValue.setName(accountStatus.getSymbols().get(value.symbol));
                availablePositions.add(posValue);
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, new RiskLimitValue(
                riskLimitValue.totalBalance, riskLimitValue.availableBalance, availablePositions));
    }

    @Override
    protected ExMessage<List<PositionValue>> getPositions() throws ExApiException {
        List<PositionValue> positionValues = new ArrayList<>();
        for (String symbol : accountStatus.getSymbols().keySet()) {
            double size = 0;
            double price = 0;
            long quantity = 0;
            PositionValue value1 = accountStatus.getPositionValues().get(symbol + POSITION_BUY);
            PositionValue value2 = accountStatus.getPositionValues().get(symbol + POSITION_SELL);
            if (value1 != null) {
                size = value1.getSize();
                price = value1.getPrice();
                quantity = value1.getQuantity();
            }
            if (value2 != null) {
                // value2 size，quantity 为负数
                size = size + value2.getSize();
                price = value2.getPrice();
                quantity = quantity + value2.getQuantity();
            }
            if (value1 != null || value2 != null) {
                positionValues.add(new PositionValue(accountStatus.getSymbols().get(symbol), symbol,
                        accountConfig.getName(), size, price, quantity,
                        0, 0, System.currentTimeMillis()));
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, positionValues);
    }

    @Override
    protected Order updateOrder(Order order) {
        if (OrderSide.BUY.equals(order.getSide())) {
            // 检查是否可以调整为平空订单
            PositionValue pos = accountStatus.getPositionValues().get(order.getSymbol() + POSITION_SELL);
            if (pos != null && pos.getSize() + order.getSize() <= 0) {
                Order updatedOrder = new Order(order.getAccount(), order.getName(),
                        order.getSymbol(), OrderSide.BUY_SHORT, order.getOrderType(),
                        order.getSize(), order.getPrice(), order.getQuantity());
                updatedOrder.setId(order.getId());
                return updatedOrder;
            }
        } else if (OrderSide.SELL.equals(order.getSide())) {
            PositionValue pos = accountStatus.getPositionValues().get(order.getSymbol() + POSITION_BUY);
            if (pos != null && pos.getSize() - order.getSize() >= 0) {
                Order updatedOrder = new Order(order.getAccount(), order.getName(),
                        order.getSymbol(), OrderSide.SELL_LONG, order.getOrderType(),
                        order.getSize(), order.getPrice(), order.getQuantity());
                updatedOrder.setId(order.getId());
                return updatedOrder;
            }
        }
        return order;
    }
}
