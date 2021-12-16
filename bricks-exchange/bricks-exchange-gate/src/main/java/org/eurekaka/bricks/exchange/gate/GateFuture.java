package org.eurekaka.bricks.exchange.gate;

import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.FutureAccountStatus;
import org.eurekaka.bricks.common.model.PositionValue;
import org.eurekaka.bricks.common.util.Utils;

import javax.crypto.Mac;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GateFuture extends AbstractFutureExchange {

//    private final String orderBookSize;

    public GateFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());
//        orderBookSize = accountConfig.getProperty("order_book_size", "20");
        // gate 只允许获取200个价格档位
        if (this.orderBookLimit > 200) {
            this.orderBookLimit = 100;
        }
    }

    @Override
    protected void postStart() throws ExchangeException {
        // 通过rest，初始化net value信息, position信息
        try {
            for (PositionValue value : api.getPositionValue(null)) {
                accountStatus.getPositionValues().put(value.getSymbol(), value);
            }
            // !!! 不能更新此处的netvalue信息
//            for (NetValue value : api.getNetValue(null)) {
//                accountStatus.getNetValues().put(value.symbol, value);
//            }
        } catch (ExApiException e) {
            throw new ExchangeException("failed to get binance future init position values", e);
        }
    }

    @Override
    protected void sendPingBuffer() {
        super.sendPingBuffer();
        this.webSocket.sendText("{\"time\" : " + System.currentTimeMillis() / 1000 +
                ", \"channel\" : \"futures.ping\"}", true);
    }

    @Override
    protected void sendSub(String symbol) throws ExApiException {
        try {
            List<String> payload = new ArrayList<>();
            payload.add(symbol);
            GateWebSocketRequest request = new GateWebSocketRequest(
                    "futures.tickers", "subscribe", payload);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request), true);
//            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
//                    "futures.trades", "subscribe", payload)), true);

            // sub order book
            payload.clear();
            payload.add(symbol);
            payload.add("1");
            payload.add("0");
            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
                    "futures.order_book", "subscribe", payload)), true);

            payload.clear();
            payload.add(symbol);
            payload.add("100ms");
            payload.add("20");
            GateWebSocketRequest request1 = new GateWebSocketRequest(
                    "futures.order_book_update", "subscribe", payload);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request1), true);

            payload.clear();
            payload.add(symbol);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
                    "futures.book_ticker", "subscribe", payload)), true);

            payload.clear();
            payload.add(accountConfig.getUid());
            payload.add(symbol);
            GateWebSocketRequest request2 = new GateWebSocketRequest(
                    "futures.usertrades", "subscribe", payload);
            doAuth(request2);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request2), true);

            GateWebSocketRequest request3 = new GateWebSocketRequest(
                    "futures.orders", "subscribe", payload);
            doAuth(request3);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request3), true);

            GateWebSocketRequest request4 = new GateWebSocketRequest(
                    "futures.positions", "subscribe", payload);
            doAuth(request4);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request4), true);
            Thread.sleep(100);
        } catch (Exception e) {
            throw new ExApiException("failed to send subscription", e);
        }
    }

    @Override
    protected void sendUnsub(String symbol) throws ExApiException {
        try {
            List<String> payload = new ArrayList<>();
            payload.add(symbol);
            GateWebSocketRequest request = new GateWebSocketRequest(
                    "futures.tickers", "unsubscribe", payload);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request), true);

            payload.clear();
            payload.add(symbol);
            payload.add("1");
            payload.add("0");
            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
                    "futures.order_book", "unsubscribe", payload)), true);

            payload.clear();
            payload.add(symbol);
            payload.add("100ms");
            payload.add("20");
            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
                    "futures.order_book_update", "unsubscribe", payload)), true);

            payload.clear();
            payload.add(symbol);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(new GateWebSocketRequest(
                    "futures.book_ticker", "unsubscribe", payload)), true);

            payload.clear();
            payload.add(accountConfig.getUid());
            payload.add(symbol);
            GateWebSocketRequest request2 = new GateWebSocketRequest(
                    "futures.usertrades", "unsubscribe", payload);
            doAuth(request2);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request2), true);

            GateWebSocketRequest request3 = new GateWebSocketRequest(
                    "futures.orders", "unsubscribe", payload);
            doAuth(request3);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request3), true);

            GateWebSocketRequest request4 = new GateWebSocketRequest(
                    "futures.positions", "unsubscribe", payload);
            doAuth(request4);
            this.webSocket.sendText(Utils.mapper.writeValueAsString(request4), true);
        } catch (Exception e) {
            throw new ExApiException("failed to send subscription", e);
        }
    }

    private void doAuth(GateWebSocketRequest request) {
        String signString = "channel=" + request.channel +
                "&event=" + request.event +
                "&time=" + request.time;
        Map<String, String> auth = new HashMap<>();
        auth.put("method", "api_key");
        auth.put("KEY", accountConfig.getAuthKey());

        Mac sha512Mac = Utils.initialHMac(accountConfig.getAuthSecret(), "HmacSHA512");
        String signature = Utils.encodeHexString(sha512Mac.doFinal(signString.getBytes()));
        auth.put("SIGN", signature);

        request.auth = auth;
    }

    @Override
    protected void updatePositionValue(PositionValue position) {
        if (accountStatus.getNetValues().containsKey(position.getSymbol())) {
            double symbolPrice = accountStatus.getNetValues()
                    .get(position.getSymbol()).getPrice() * 1.0 / Utils.PRECISION;
            position.setPrice(symbolPrice);
            position.setQuantity(Math.round(position.getSize() * symbolPrice));
            position.setUnPnl(position.getSize() * (symbolPrice - position.getEntryPrice()));
        }
    }

}
