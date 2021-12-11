package org.eurekaka.bricks.exchange.binance;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class BinanceFutureListener extends WebSocketListener<FutureAccountStatus, BinanceFutureApi> {
    private final ObjectReader reader1;
    private final ObjectReader reader2;
    private final ObjectReader reader3;
    private long start;

    public BinanceFutureListener(AccountConfig accountConfig,
                                 FutureAccountStatus accountStatus,
                                 BinanceFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);
        reader1 = Utils.mapper.reader().forType(BinanceWebSocketMsgV2.class);
        reader2 = Utils.mapper.reader().forType(SocketBookTicker.class);
        reader3 = Utils.mapper.reader().forType(BinanceWebSocketMsg.class);
        start = System.currentTimeMillis();
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        JsonNode node = Utils.mapper.readTree(message);
        if (!node.has("e")) {
            return;
        }
        String eventName = node.get("e").asText();
        if ("depthUpdate".equals(eventName)) {
            BinanceWebSocketMsgV2 msg = reader1.readValue(node);
//                bidPrices.put(msg.symbol, msg.getLastBidPrice());
//                sendLastPrice(new ExLastPrice(name, msg.symbol, "bid", msg.getLastBidPrice()));
//                askPrices.put(msg.symbol, msg.getLastAskPrice());
//                sendLastPrice(new ExLastPrice(name, msg.symbol, "ask", msg.getLastAskPrice()));
//            if (!msg.bids.isEmpty()) {
//                TreeMap<Double, Double> bidOrderBook = new TreeMap<>(Comparator.reverseOrder());
//                for (List<Double> bid : msg.bids) {
//                    bidOrderBook.put(bid.get(0), bid.get(1));
//                }
//                accountStatus.getBidOrderBooks().put(msg.symbol, bidOrderBook);
//            }

//            if (!msg.asks.isEmpty()) {
//                TreeMap<Double, Double> askOrderBook = new TreeMap<>(Comparator.naturalOrder());
//                for (List<Double> ask : msg.asks) {
//                    askOrderBook.put(ask.get(0), ask.get(1));
//                }
//                accountStatus.getAskOrderBooks().put(msg.symbol, askOrderBook);
//            }
            OrderBookValue orderBookValue = new OrderBookValue(msg.lastUpdateId - 1, msg.parentUpdateId,
                    OrderBookValue.parsePairs(msg.bids), OrderBookValue.parsePairs(msg.asks));
            if (!accountStatus.updateOrderBookValue(msg.symbol, orderBookValue)) {
                logger.warn("failed to update order book value, no serial update id: {}", orderBookValue);
                api.asyncGetOrderBook(msg.symbol, orderBookLimit).thenAccept(value -> {
                    accountStatus.buildOrderBookValue(msg.symbol, value);
                });
            }

//            long timer = System.currentTimeMillis() - start;
//            double bid = 0;
//            double ask = 0;
//            TreeMap<Double, Double> bidMap = accountStatus.getBidOrderBooks().get(msg.symbol);
//            if (bidMap != null && !bidMap.isEmpty()) {
//                bid = bidMap.firstKey();
//            }
//            TreeMap<Double, Double> askMap = accountStatus.getAskOrderBooks().get(msg.symbol);
//            if (askMap != null && !askMap.isEmpty()) {
//                ask = askMap.firstKey();
//            }
//            logger.info("{}: bid: {}, ask: {}, depth update message: {}", timer, bid, ask, message);
        } else if ("bookTicker".equals(eventName)) {
            // 使用bookTicker更新 order book 的买一卖一
//            logger.info("bookTicker message: {}", message);
            SocketBookTicker msg = reader2.readValue(node);

            if (msg.bidPrice > 0 && msg.bidSize > 0) {
                accountStatus.updateBidOrderBookTicker(msg.symbol, msg.bidPrice, msg.bidSize);
            }

            if (msg.askPrice > 0 && msg.askSize > 0) {
                accountStatus.updateAskOrderBookTicker(msg.symbol, msg.askPrice, msg.askSize);
            }
        } else {
            BinanceWebSocketMsg msg = reader3.readValue(node);
            if ("kline".equals(msg.eventName)) {
                String name = accountStatus.getSymbols().get(msg.symbol);
                if (name != null && msg.kLine != null) {
                    BinanceKLineData kline = msg.kLine;
                    KLineValue value = new KLineValue(kline.startTime, name, msg.symbol,
                            kline.open, kline.close, kline.highest, kline.lowest, kline.volume);
                    // 比较最后一根k线的时间
                    List<KLineValue> kLineValues = accountStatus.getKlineValues().get(msg.symbol);
                    if (kLineValues != null) {
                        KLineValue lastValue = kLineValues.get(kLineValues.size() - 1);
                        if (lastValue.time == value.time) {
                            kLineValues.remove(kLineValues.size() - 1);
                            kLineValues.add(value);
                        } else if (lastValue.time < value.time) {
                            kLineValues.remove(0);
                            kLineValues.add(value);
                        }
                    }
                }
            } else if ("markPriceUpdate".equals(msg.eventName)) {
                long price = Math.round(msg.price * PRECISION);
                if (price > 0) {
                    accountStatus.getNetValues().put(msg.symbol, new NetValue(
                            msg.symbol, accountConfig.getName(), price));
                }
                accountStatus.getFundingRates().put(msg.symbol, msg.fundingRate);
            } else if ("ACCOUNT_UPDATE".equals(msg.eventName)) {
//                    for (BinanceAccountBalance accountBalance : msg.accountUpdate.accountBalances) {
//                        currentAccount.put(accountBalance.asset, accountBalance.walletBalance);
//                    }
                for (BinanceAccountPosition accountPosition : msg.accountUpdate.accountPositions) {
                    if ("BOTH".equals(accountPosition.positionDirection)) {
                        accountStatus.getPositionValues().put(accountPosition.symbol,
                                new PositionValue(accountPosition.symbol, accountConfig.getName(),
                                        accountPosition.amount, 0, 0,
                                        accountPosition.entryPrice, accountPosition.unrealizedPnl));
                    }
                }
            } else if ("ORDER_TRADE_UPDATE".equals(msg.eventName)) {
                BinanceOrderUpdate update = msg.orderUpdate;
//                logger.debug("order trade update: {}", message);
                if (accountStatus.getNotificationQueue() != null) {
//                    System.out.println(message);
                    String name = accountStatus.getSymbols().get(update.symbol);
                    if (name != null) {
                        OrderSide side = OrderSide.NONE;
                        if ("SELL".equals(update.side)) {
                            side = OrderSide.SELL;
                        } else if ("BUY".equals(update.side)) {
                            side = OrderSide.BUY;
                        }
                        OrderType type = BinanceUtils.getOrderType(update.type, update.tif);
                        if (update.id != null && !update.id.equals("0")) {
                            accountStatus.getNotificationQueue().add(new TradeNotification(update.id,
                                    update.clientOrderId, update.order_id, accountConfig.getName(), name, update.symbol,
                                    side, type, update.price, update.size, update.price * update.size,
                                    update.feeSymbol, update.fee, update.time));
                        }
                        accountStatus.getNotificationQueue().add(new OrderNotification(
                                update.order_id, name, update.symbol, accountConfig.getName(),
                                side, type, update.orderSize, update.orderPrice, update.filledSize,
                                update.avgPrice, update.clientOrderId,
                                BinanceUtils.getStatus(update.state), update.time));
                    }
                }
            } else if ("listenKeyExpired".equals(msg.eventName)) {
                try {
                    webSocket.sendText(api.getAuthMessage(), true);
                } catch (Exception e) {
                    logger.error("failed to resend new listenKey", e);
                    // stop, and it will reconnect from outside.
                    webSocket.abort();
                }
            }
        }
    }

    static class BinanceWebSocketMsg {
        @JsonProperty("e")
        public String eventName;
        @JsonProperty("E")
        public long eventTime;
        @JsonProperty("a")
        public BinanceAccountUpdate accountUpdate;
        @JsonProperty("o")
        public BinanceOrderUpdate orderUpdate;

        @JsonProperty("s")
        public String symbol;
        @JsonProperty("p")
        public double price;
        @JsonProperty("r")
        public double fundingRate;
        @JsonProperty("k")
        public BinanceKLineData kLine;

        public BinanceWebSocketMsg() {
        }
    }

    static class BinanceWebSocketMsgV2 {
        @JsonProperty("b")
        public List<List<Double>> bids;
        @JsonProperty("a")
        public List<List<Double>> asks;
        @JsonProperty("s")
        public String symbol;
        @JsonProperty("U")
        public long firstUpdateId;
        @JsonProperty("u")
        public long lastUpdateId;
        @JsonProperty("pu")
        public long parentUpdateId;


        public BinanceWebSocketMsgV2() {
        }
    }

    static class BinanceAccountUpdate {
        @JsonProperty("B")
        public List<BinanceAccountBalance> accountBalances;

        @JsonProperty("P")
        public List<BinanceAccountPosition> accountPositions;

        public BinanceAccountUpdate() {
        }
    }

    static class BinanceAccountBalance {
        @JsonProperty("a")
        public String asset;

        @JsonProperty("wb")
        public double walletBalance;

        public BinanceAccountBalance() {
        }
    }

    static class BinanceAccountPosition {
        @JsonProperty("s")
        public String symbol;
        @JsonProperty("pa")
        public double amount;
        @JsonProperty("cr")
        public double cumRealizedPnl;
        @JsonProperty("up")
        public double unrealizedPnl;
        @JsonProperty("ep")
        public double entryPrice;
        @JsonProperty("ps")
        public String positionDirection;

        public BinanceAccountPosition() {
        }
    }

    static class BinanceOrderUpdate {
        @JsonProperty("t")
        public String id;
        @JsonProperty("i")
        public String order_id;
        @JsonProperty("c")
        public String clientOrderId;
        @JsonProperty("s")
        public String symbol;
        @JsonProperty("S")
        public String side;
        @JsonProperty("L")
        public double price;
        @JsonProperty("l")
        public double size;
        @JsonProperty("N")
        public String feeSymbol;
        @JsonProperty("n")
        public double fee;
        @JsonProperty("T")
        public long time;
        @JsonProperty("o")
        public String type;
        @JsonProperty("f")
        public String tif;
        @JsonProperty("X")
        public String state;
        @JsonProperty("x")
        public String executeType;
        @JsonProperty("z")
        public double filledSize;
        @JsonProperty("ap")
        public double avgPrice;
        @JsonProperty("q")
        public double orderSize;
        @JsonProperty("p")
        public double orderPrice;

        public BinanceOrderUpdate() {
        }

    }

    static class BinanceKLineData {
        @JsonProperty("t")
        public long startTime;
        @JsonProperty("T")
        public long stopTime;
        @JsonProperty("o")
        public double open;
        @JsonProperty("c")
        public double close;
        @JsonProperty("h")
        public double highest;
        @JsonProperty("l")
        public double lowest;
        @JsonProperty("v")
        public double volume;

        public BinanceKLineData() {
        }
    }
}
