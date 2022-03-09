package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.*;
import java.util.concurrent.Executor;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class GateFutureListener extends WebSocketListener<FutureAccountStatus, GateFutureApi> {

    private final ObjectReader reader;
    private final ObjectReader reader1;
    private final ObjectReader reader2;
    private final ObjectReader reader3;
    private long start;

    public GateFutureListener(AccountConfig accountConfig,
                              FutureAccountStatus accountStatus,
                              GateFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);

        reader = Utils.mapper.reader().forType(GateWebSocketResp.class);
        reader1 = Utils.mapper.reader(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .forType(new TypeReference<List<GateWebSocketResult>>() {});
        reader2 = Utils.mapper.reader().forType(GateWebSocketResultV2.class);
        reader3 = Utils.mapper.reader().forType(GateWebSocketResultV3.class);
        start = System.currentTimeMillis();

        if (orderBookLimit > 200) {
            orderBookLimit = 20;
        }
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        GateWebSocketResp resp = reader.readValue(message);
        if ("futures.pong".equals(resp.channel)) {
            accountStatus.updateLastPongTime();
        } else if ("futures.tickers".equals(resp.channel) && "update".equals(resp.event)) {
            List<GateWebSocketResult> results = reader1.readValue(resp.result);
            for (GateWebSocketResult result : results) {
                long price = Math.round(result.mark_price * PRECISION);
                if (price > 0) {
                    NetValue value = new NetValue(result.contract, accountConfig.getName(), price);
                    accountStatus.getNetValues().put(result.contract, value);
                }
                accountStatus.getFundingRates().put(result.contract, result.funding_rate);
            }
        } else if ("futures.order_book".equals(resp.channel) && "all".equals(resp.event)) {
            GateWebSocketResultV3 result = reader3.readValue(resp.result);
            if (result.bids != null && result.bids.size() == 1) {
                GatePriceSizePair bid = result.bids.get(0);
                accountStatus.updateBidOrderBookTicker(result.symbol, bid.price, bid.size);
            }
            if (result.asks != null && result.asks.size() == 1) {
                GatePriceSizePair ask = result.asks.get(0);
                accountStatus.updateAskOrderBookTicker(result.symbol, ask.price, ask.size);
            }
//            long timer = System.currentTimeMillis() - start;
//            double bid = 0;
//            double ask = 0;
//            TreeMap<Double, Double> bidMap = accountStatus.getBidOrderBooks().get(result.symbol);
//            if (bidMap != null && !bidMap.isEmpty()) {
//                bid = bidMap.firstKey();
//            }
//            TreeMap<Double, Double> askMap = accountStatus.getAskOrderBooks().get(result.symbol);
//            if (askMap != null && !askMap.isEmpty()) {
//                ask = askMap.firstKey();
//            }
//            logger.info("{}: bid: {}, ask: {}, order book message: {}", timer, bid, ask, message);
//        } else if ("futures.trades".equals(resp.channel) && "update".equals(resp.event)) {
//            List<GateWebSocketResult> results = reader1.readValue(resp.result);
//            for (GateWebSocketResult result : results) {
//                if (result.size > 0) {
//                    // 买单，则卖一可能已经没有
//                    accountStatus.updateAskOrderBookTicker(result.contract, result.price, 0);
//                } else {
//                    accountStatus.updateBidOrderBookTicker(result.contract, result.price, 0);
//                }
//            }
        } else if ("futures.order_book_update".equals(resp.channel) && "update".equals(resp.event)) {
            GateWebSocketResultV3 result = reader3.readValue(resp.result);
            logger.info("book update, elapsed time: {}, message: {}",
                    System.currentTimeMillis() - result.time, message);
            List<OrderBookValue.PriceSizePair> bidPairs = new ArrayList<>();
            for (GatePriceSizePair bid : result.bids) {
                bidPairs.add(new OrderBookValue.PriceSizePair(bid.price, api.getSize(result.symbol, bid.size)));
            }
            List<OrderBookValue.PriceSizePair> askPairs = new ArrayList<>();
            for (GatePriceSizePair ask : result.asks) {
                askPairs.add(new OrderBookValue.PriceSizePair(ask.price, api.getSize(result.symbol, ask.size)));
            }
//            accountStatus.updateOrderBook(result.symbol, bidPairs, askPairs);
            OrderBookValue orderBookValue = new OrderBookValue(result.lastUpdateId,
                    result.firstUpdateId, bidPairs, askPairs);
            if (!accountStatus.updateOrderBookValue(result.symbol, orderBookValue)) {
                logger.warn("failed to update order book value, no serial update id: {}", orderBookValue);
                api.asyncGetOrderBook(result.symbol, orderBookLimit).thenAccept(value -> {
                    accountStatus.buildOrderBookValue(result.symbol, value);
                });
            }
//            long timer = System.currentTimeMillis() - start;
//            double bid = 0;
//            double ask = 0;
//            TreeMap<Double, Double> bidMap = accountStatus.getBidOrderBooks().get(result.symbol);
//            if (bidMap != null && !bidMap.isEmpty()) {
//                bid = bidMap.firstKey();
//            }
//            TreeMap<Double, Double> askMap = accountStatus.getAskOrderBooks().get(result.symbol);
//            if (askMap != null && !askMap.isEmpty()) {
//                ask = askMap.firstKey();
//            }
//            logger.info("{}: bid: {}, ask: {}, order book update message: {}", timer, bid, ask, message);
//            if (timer > 100000) {
//                start = start + timer;
//                logger.info("order book value: {}", orderBookValue);
//                logger.info("order book values size: {}", accountStatus.getOrderBookValues().get(result.symbol).size());
//                logger.info("order book bids: {}", accountStatus.getBidOrderBooks().get(result.symbol));
//                logger.info("order book asks: {}", accountStatus.getAskOrderBooks().get(result.symbol));
//            }
        } else if ("futures.book_ticker".equals(resp.channel) && "update".equals(resp.event)) {
            GateWebSocketResultV2 result = reader2.readValue(resp.result);
            logger.info("book ticker, elapsed time: {}, message: {}",
                    System.currentTimeMillis() - result.time, message);
            if (result.bidPrice > 0 && result.bidSize != 0) {
//                accountStatus.updateBidOrderBookTicker(result.contract,
//                        result.bidPrice, api.getSize(result.contract, result.bidSize));
                accountStatus.updateTopBid(result.contract, accountConfig.getName(), result.bidPrice);
            }
            if (result.askPrice > 0 && result.askSize != 0) {
//                accountStatus.updateAskOrderBookTicker(result.contract,
//                        result.askPrice, api.getSize(result.contract, result.askSize));
                accountStatus.updateTopAsk(result.contract, accountConfig.getName(), result.askPrice);
            }
//            long timer = System.currentTimeMillis() - start;
//            double bid = 0;
//            double ask = 0;
//            TreeMap<Double, Double> bidMap = accountStatus.getBidOrderBooks().get(result.contract);
//            if (bidMap != null && !bidMap.isEmpty()) {
//                bid = bidMap.firstKey();
//            }
//            TreeMap<Double, Double> askMap = accountStatus.getAskOrderBooks().get(result.contract);
//            if (askMap != null && !askMap.isEmpty()) {
//                ask = askMap.firstKey();
//            }
//            logger.info("{}: bid: {}, ask: {}, book ticker message: {}", timer, bid, ask, message);
        } else if ("futures.usertrades".equals(resp.channel) && "update".equals(resp.event)) {
            List<GateWebSocketResult> results = reader1.readValue(resp.result);
            for (GateWebSocketResult result : results) {
                String name = accountStatus.getSymbols().get(result.contract);
                if (name != null) {
                    OrderSide side = OrderSide.BUY;
                    double size = result.size;
                    if (result.size < 0) {
                        side = OrderSide.SELL;
                        size = -size;
                    }
                    size = api.getSize(result.contract, size);
                    OrderType type = OrderType.MARKET;
                    double rate = accountConfig.getTakerRate();
                    if ("maker".equals(result.role)) {
                        type = OrderType.LIMIT;
                        rate = accountConfig.getMakerRate();
                    }
                    TradeNotification notification = new TradeNotification(result.id,
                            GateUtils.getClientOrderId(result.text), result.order_id,
                            accountConfig.getName(), name, result.contract, side, type,
                            result.price, size, result.price * size,
                            "USDT", result.price * size * rate, result.create_time_ms);
                    accountStatus.getNotificationQueue().add(notification);
                }
            }
        } else if ("futures.orders".equals(resp.channel) && "update".equals(resp.event)) {
            List<GateWebSocketResult> results = reader1.readValue(resp.result);
            for (GateWebSocketResult result : results) {
                String name = accountStatus.getSymbols().get(result.contract);
                if (name != null) {
                    OrderSide side = OrderSide.BUY;
                    if (result.size < 0) {
                        side = OrderSide.SELL;
                    }

                    OrderType type = GateUtils.getOrderType(result.price, result.tif);
                    double size = Math.abs(api.getSize(result.contract, result.size));
                    double leftSize = Math.abs(api.getSize(result.contract, result.left));
                    double filled = size - leftSize;

                    String clientOrderId = GateUtils.getClientOrderId(result.text);
                    OrderNotification notification = new OrderNotification(result.id, name,
                            result.contract, accountConfig.getName(), side, type,
                            size, result.price, filled,
                            result.fill_price, clientOrderId,
                            GateUtils.getStatus(result.status, result.finish_as, filled),
                            result.finish_time_ms);
                    accountStatus.getNotificationQueue().add(notification);
                }
            }
        } else if ("futures.positions".equals(resp.channel) && "update".equals(resp.event)) {
            List<GateWebSocketResult> results = reader1.readValue(resp.result);
            for (GateWebSocketResult result : results) {
                if ("single".equals(result.mode)) {
                    String name = accountStatus.getSymbols().get(result.contract);
                    if (name != null) {
                        double size = api.getSize(result.contract, result.size);
                        PositionValue value = new PositionValue(name, result.contract,
                                accountConfig.getName(), size, 0, 0,
                                result.entry_price, 0, result.time_ms);
                        accountStatus.getPositionValues().put(result.contract, value);
                    }
                }
            }
        }
    }
}
