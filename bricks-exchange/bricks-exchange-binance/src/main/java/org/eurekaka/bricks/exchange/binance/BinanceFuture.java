package org.eurekaka.bricks.exchange.binance;

import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

public class BinanceFuture extends AbstractFutureExchange {
    private long lastListenKeyTime;

    public BinanceFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());
    }

    @Override
    public void start() throws ExchangeException {
        super.start();

        this.lastListenKeyTime = System.currentTimeMillis();

        // 通过rest，初始化net value信息, position信息
        try {
            for (PositionValue value : api.getPositionValue(null)) {
                accountStatus.getPositionValues().put(value.getSymbol(), value);
            }
            // !!! 不能更新此处的netvalue信息
//            for (NetValue value : api.getNetValue(null)) {
//                accountStatus.getNetValues().put(value.symbol, value);
//            }
            // 获取历史KLine

        } catch (ExApiException e) {
            throw new ExchangeException("failed to get binance future init position values", e);
        }
    }

    @Override
    protected void sendSub(String symbol) throws ExApiException {
        try {
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new BinanceSocketSub("SUBSCRIBE",
                            symbol.toLowerCase() + "@markPrice@1s",
                            symbol.toLowerCase() + "@depth@100ms"
//                            symbol.toLowerCase() + "@bookTicker"
                    )), true);
            if (enableKlineSub) {
                webSocket.sendText(Utils.mapper.writeValueAsString(
                        new BinanceSocketSub("SUBSCRIBE",
                                symbol.toLowerCase() + "@kline_" + klineInterval.value)), true);
            }
            Thread.sleep(300);
        } catch (Exception e) {
            throw new ExApiException("failed to send subscription", e);
        }
    }

    @Override
    protected void sendUnsub(String symbol) throws ExApiException {
        try {
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new BinanceSocketSub("UNSUBSCRIBE",
                            symbol.toLowerCase() + "@markPrice@1s",
                            symbol.toLowerCase() + "@depth@100ms"
//                            symbol.toLowerCase() + "@bookTicker"
                    )), true);
            if (enableKlineSub) {
                webSocket.sendText(Utils.mapper.writeValueAsString(
                        new BinanceSocketSub("UNSUBSCRIBE",
                                symbol.toLowerCase() + "@kline_" + klineInterval.value)), true);
            }
            Thread.sleep(300);
        } catch (Exception e) {
            throw new ExApiException("failed to send unsubscription", e);
        }
    }

    @Override
    protected void updatePositionValue(PositionValue position) {
        if (accountStatus.getNetValues().containsKey(position.getSymbol())) {
            double symbolPrice = accountStatus.getNetValues()
                    .get(position.getSymbol()).getPrice() * 1.0 / Utils.PRECISION;
            position.setPrice(symbolPrice);
            position.setQuantity(Math.round(position.getSize() * symbolPrice));
        }
    }

    @Override
    protected void sendPingBuffer() {
        super.sendPingBuffer();
        if (System.currentTimeMillis() - lastListenKeyTime > 55 * 60 * 1000) {
            try {
                webSocket.sendText(api.getAuthMessage(), true);
                lastListenKeyTime = System.currentTimeMillis();
            } catch (ExApiException e) {
                logger.error("failed to send auth message for binance listenkey", e);
            }
        }
    }
}
