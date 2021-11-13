package org.eurekaka.bricks.exchange.bhex;

import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.WeChatReporter;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.eurekaka.bricks.common.model.ReportEvent.EventType.HEDGING_AGENT_FAILED;

/**
 * bhex合约挂单做市程序
 * bhex websocket 需要每小时延迟连接，还24小时重新连接一次。。。
 * 什么鬼玩意，这样坑用户
 * 该future不可作为etf对冲合约
 */
public class BhexFuture extends AbstractFutureExchange {
    private long lastListenKeyTime;
    private String listenKey;

    public BhexFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());
    }

    @Override
    public void start() throws ExchangeException {
        try {
            listenKey = ((BhexFutureApi) api).getListenKey();
            String socket = accountConfig.getWebsocket() + "/openapi/ws/" + listenKey;
            webSocket = HttpUtils.createWebSocket(httpClient, listener, socket, 10);
            lastListenKeyTime = System.currentTimeMillis();

            this.accountStatus.updateLastPongTime();

            // 获取仓位
            for (Map.Entry<String, BhexPosValue> entry : ((BhexFutureApi) api).getBhexPositions().entrySet()) {
                BhexPosValue value = entry.getValue();
                accountStatus.getPositionValues().put(entry.getKey(), new PositionValue(value.symbol,
                        accountConfig.getName(), value.availSize, value.lastPrice, value.positionValue,
                        0, 0));
            }
        } catch (Exception e) {
            throw new ExchangeException("failed to start " + accountConfig.getName(), e);
        }

        startWebSocketMonitor();

        logger.info("account started: {}", accountConfig.getName());
    }

    @Override
    protected void sendPingBuffer() {
        long currentTime = System.currentTimeMillis();
        webSocket.sendPing(ByteBuffer.wrap(("{\"ping\":" + currentTime +"}").getBytes()));
        // 每隔50 min，发送 listenKey delay
        if (currentTime - lastListenKeyTime >= 50 * 60000) {
            try {
                ((BhexFutureApi) api).keepListenKey(listenKey);
                lastListenKeyTime = currentTime;
            } catch (ExApiException e) {
                logger.error("failed to keep listen key", e);
            }
        }
    }

    @Override
    protected Order updateOrder(Order order) {
        PositionValue value1 = accountStatus.getPositionValues().get(order.getSymbol() + "LONG");
        PositionValue value2 = accountStatus.getPositionValues().get(order.getSymbol() + "SHORT");
        double size = 0;
        OrderSide side = order.getSide();
        if (order.getSide().equals(OrderSide.BUY)) {
            if (value2 != null && value2.getSize() >= order.getSize()) {
                side = OrderSide.BUY_SHORT;
            }
        } else if (order.getSide().equals(OrderSide.SELL)) {
            if (value1 != null && value1.getSize() >= order.getSize()) {
                side = OrderSide.SELL_LONG;
            }
        }
        Order o = new Order(order.getAccount(), order.getName(), order.getSymbol(), side,
                order.getOrderType(), order.getSize(), order.getPrice(), order.getQuantity());
        o.setId(order.getId());

        return o;
    }

    @Override
    protected ExMessage<List<PositionValue>> getPositions() throws ExApiException {
        List<PositionValue> positions = api.getPositionValue(null);
        List<PositionValue> positionValues = new ArrayList<>();
        for (PositionValue position : positions) {
            String name = accountStatus.getSymbols().get(position.getSymbol());
            if (name != null) {
                position.setName(name);
                positionValues.add(position.copy());
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, positionValues);
    }

}
