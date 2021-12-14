package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FtxFuture extends AbstractFutureExchange {
    private final long fetchInterval;
    private final String currencyRateSymbol;

    public FtxFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());

        // 时间间隔
        fetchInterval = Long.parseLong(
                accountConfig.getProperty("fetch_interval", "30000"));
        currencyRateSymbol = accountConfig.getProperty("currency_rate_symbol", "USDT/USD");

        httpClient.executor().get().execute(new FtxFetcher());
    }

    @Override
    protected void postStart() throws ExchangeException {
        super.postStart();

        try {
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("subscribe", "fills")), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("subscribe", "orders")), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("subscribe", "ticker", currencyRateSymbol)), true);

            // 更新账户余额
            api.asyncGetAccountValues().thenAccept(accountValues -> {
                for (AccountValue accountValue : accountValues) {
                    accountStatus.getBalances().put(accountValue.asset, accountValue);
                }
            });

            api.asyncGetPositionValues().thenAccept(positionValues -> {
                for (PositionValue positionValue : positionValues) {
                    accountStatus.getPositionValues().put(positionValue.getSymbol(), positionValue);
                }
            });
        } catch (Exception e) {
            throw new ExchangeException("failed to start ftx future exchange", e);
        }
    }

    @Override
    protected void sendSub(String symbol) throws ExApiException {
        try {
            updateFundingRate(symbol);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("subscribe", "ticker", symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("subscribe", "orderbook", symbol)), true);
        } catch (Exception e) {
            throw new ExApiException("failed to send sub for: " + symbol, e);
        }
    }

    @Override
    protected void sendUnsub(String symbol) throws ExApiException {
        try {
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("unsubscribe", "ticker", symbol)), true);
            webSocket.sendText(Utils.mapper.writeValueAsString(
                    new FtxWebSocketMsg("unsubscribe", "orderbook", symbol)), true);
        } catch (Exception e) {
            throw new ExApiException("failed to send unsub for: " + symbol, e);
        }
    }


//    @Override
//    protected ExMessage<List<PositionValue>> getPositions() throws ExApiException {
//        List<PositionValue> positions = api.getPositionValue(null);
//        List<PositionValue> positionValues = new ArrayList<>();
//        for (PositionValue position : positions) {
//            String name = accountStatus.getSymbols().get(position.getSymbol());
//            if (name != null) {
//                position.setName(name);
//                positionValues.add(position);
//            }
//        }
//        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, positionValues);
//    }

    @Override
    protected ExMessage<List<AccountValue>> getBalances() throws ExApiException {
        List<AccountValue> accountValues = api.getAccountValue();
        // 合并usd与usdt
        List<AccountValue> results = new ArrayList<>();
        double sumTotal = 0;
        double sumAvail = 0;
        for (AccountValue accountValue : accountValues) {
            if ("USD".equals(accountValue.asset)) {
                sumTotal += accountValue.totalBalance / accountStatus.getMarkUsdt();
                sumAvail += accountValue.availableBalance / accountStatus.getMarkUsdt();
            } else if ("USDT".equals(accountValue.asset)) {
                sumTotal += accountValue.totalBalance;
                sumAvail += accountValue.availableBalance;
            } else {
                results.add(accountValue);
            }
        }
        results.add(new AccountValue("USDT", accountConfig.getName(), sumTotal, sumAvail));
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, results);
    }

    private void updateFundingRate(String symbol) throws ExApiException {
        api.asyncGetFundingRate(symbol).thenAccept(rate -> {
            accountStatus.getFundingRates().put(symbol, 8 * rate);
        });
    }

    @Override
    protected void sendPingBuffer() {
        super.sendPingBuffer();
        if (webSocket != null) {
            webSocket.sendText(FtxUtils.FTX_PING_BUFFER, true);
        }
    }

    private class FtxFetcher implements Runnable {
        private final AtomicBoolean exited;
        private long nextFetchTime;

        public FtxFetcher() {
            this.exited = new AtomicBoolean(false);
            this.nextFetchTime = System.currentTimeMillis() / fetchInterval * fetchInterval + fetchInterval;
        }

        @Override
        public void run() {
            while (!exited.get()) {
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > nextFetchTime) {
                        for (String symbol : accountStatus.getSymbols().keySet()) {
                            updateFundingRate(symbol);
                            // 防止超频率
                            Thread.sleep(100);
                        }

                        api.asyncGetAccountValues().thenAccept(accountValues -> {
                            for (AccountValue accountValue : accountValues) {
                                accountStatus.getBalances().put(accountValue.asset, accountValue);
                            }
                        });

                        api.asyncGetPositionValues().thenAccept(positionValues -> {
                            for (PositionValue positionValue : positionValues) {
                                accountStatus.getPositionValues().put(positionValue.getSymbol(), positionValue);
                            }
                        });

                        nextFetchTime = currentTime / fetchInterval * fetchInterval + fetchInterval;
                    }

                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    exited.set(true);
                    logger.info("ftx fetcher exited.");
                } catch (Exception e) {
                    logger.error("failed to run ftx fetcher", e);
                }
            }
        }
    }

}
