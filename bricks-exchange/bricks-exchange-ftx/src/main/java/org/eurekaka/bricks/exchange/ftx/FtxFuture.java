package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.api.AbstractFutureExchange;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FtxFuture extends AbstractFutureExchange {

    private final long fetchInterval;

    public FtxFuture(AccountConfig accountConfig) {
        super(accountConfig, new FutureAccountStatus());

        // 时间间隔
        fetchInterval = Long.parseLong(
                accountConfig.getProperty("fetch_interval", "5000"));

        httpClient.executor().get().execute(new FtxFetcher());
    }

    @Override
    protected void postStart() throws ExchangeException {
        super.postStart();

        webSocket.sendText("{\"op\": \"subscribe\", \"channel\": \"fills\"}", true);
        webSocket.sendText("{\"op\": \"subscribe\", \"channel\": \"orders\"}", true);
        webSocket.sendText("{\"op\":\"subscribe\",\"channel\":\"ticker\",\"market\":\"USDT-PERP\"}", true);

        try {
            // 更新账户余额
            for (AccountValue value : api.getAccountValue()) {
                accountStatus.getBalances().put(value.asset, value);
            }

            // 更新仓位
            for (PositionValue value : api.getPositionValue(null)) {
                accountStatus.getPositionValues().put(value.getSymbol(), value);
            }
        } catch (Exception e) {
            throw new ExchangeException("failed to start ftx future exchange", e);
        }
    }

    @Override
    protected void sendSub(String symbol) throws ExApiException {
        webSocket.sendText("{\"op\": \"subscribe\", \"channel\": " +
                "\"ticker\", \"market\": \"" + symbol + "\"}", true);
        webSocket.sendText("{\"op\": \"subscribe\", \"channel\": " +
                "\"orderbook\", \"market\": \"" + symbol + "\"}", true);
        try {
            updateFundingRate(symbol);
            Thread.sleep(100);
        } catch (InterruptedException e) {
            logger.error("failed to get funding rate while waiting.");
        }
    }

    @Override
    protected void sendUnsub(String symbol) throws ExApiException {
        webSocket.sendText("{\"op\": \"unsubscribe\", " +
                "\"channel\": \"ticker\", \"market\": \"" + symbol + "\"}", true);
        webSocket.sendText("{\"op\": \"unsubscribe\", " +
                "\"channel\": \"orderbook\", \"market\": \"" + symbol + "\"}", true);
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
        accountStatus.getFundingRates().put(symbol, 8 * api.getFundingRate(symbol));
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

                        // 更新账户余额
                        for (AccountValue value : api.getAccountValue()) {
                            accountStatus.getBalances().put(value.asset, value);
                        }

                        // 更新仓位
                        for (PositionValue value : api.getPositionValue(null)) {
                            accountStatus.getPositionValues().put(value.getSymbol(), value);
                        }

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
