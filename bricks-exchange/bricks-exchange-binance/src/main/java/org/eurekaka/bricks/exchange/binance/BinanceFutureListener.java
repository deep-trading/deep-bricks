package org.eurekaka.bricks.exchange.binance;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class BinanceFutureListener extends WebSocketListener<FutureAccountStatus, BinanceFutureApi> {

    public BinanceFutureListener(AccountConfig accountConfig,
                                 FutureAccountStatus accountStatus, BinanceFutureApi api) {
        super(accountConfig, accountStatus, api);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        if ("ping".equals(message)) {
            webSocket.sendText("pong", true);
        } else if (message.contains("depthUpdate")) {
//                logger.info("depth update message: {}", message);
            BinanceWebSocketMsgV2 msg = Utils.mapper.readValue(message, BinanceWebSocketMsgV2.class);
//                bidPrices.put(msg.symbol, msg.getLastBidPrice());
//                sendLastPrice(new ExLastPrice(name, msg.symbol, "bid", msg.getLastBidPrice()));
//                askPrices.put(msg.symbol, msg.getLastAskPrice());
//                sendLastPrice(new ExLastPrice(name, msg.symbol, "ask", msg.getLastAskPrice()));
            if (!msg.bids.isEmpty()) {
                TreeMap<Double, Double> bidOrderBook = new TreeMap<>(Comparator.reverseOrder());
                for (List<Double> bid : msg.bids) {
                    bidOrderBook.put(bid.get(0), bid.get(1));
                }
                accountStatus.getBidOrderBooks().put(msg.symbol, bidOrderBook);
            }

            if (!msg.asks.isEmpty()) {
                TreeMap<Double, Double> askOrderBook = new TreeMap<>();
                for (List<Double> ask : msg.asks) {
                    askOrderBook.put(ask.get(0), ask.get(1));
                }
                accountStatus.getAskOrderBooks().put(msg.symbol, askOrderBook);
            }
        } else if (message.contains("bookTicker")) {
            // 使用bookTicker更新 order book 的买一卖一
            SocketBookTicker msg = Utils.mapper.readValue(message, SocketBookTicker.class);
            if (accountStatus.getBidOrderBooks().containsKey(msg.symbol)) {
                TreeMap<Double, Double> map = accountStatus.getBidOrderBooks().get(msg.symbol);
                boolean removed = map.entrySet().removeIf(entry -> entry.getKey() > msg.bidPrice);
                if (removed) {
                    map.put(msg.bidPrice, msg.bidSize);
                }
            }
            if (accountStatus.getAskOrderBooks().containsKey(msg.symbol)) {
                TreeMap<Double, Double> map = accountStatus.getAskOrderBooks().get(msg.symbol);
                boolean removed = map.entrySet().removeIf(entry -> entry.getKey() < msg.askPrice);
                if (removed) {
                    map.put(msg.askPrice, msg.askSize);
                }
            }
        } else {
            BinanceWebSocketMsg msg = Utils.mapper.readValue(message, BinanceWebSocketMsg.class);
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
                    String name = accountStatus.getSymbols().get(update.symbol);
                    if (name != null) {
                        OrderSide side = OrderSide.NONE;
                        if ("SELL".equals(update.side)) {
                            side = OrderSide.SELL;
                        } else if ("BUY".equals(update.side)) {
                            side = OrderSide.BUY;
                        }
                        OrderType type = OrderType.NONE;
                        if ("LIMIT".equals(update.type)) {
                            type = OrderType.LIMIT;
                        } else if ("MARKET".equals(update.type)) {
                            type = OrderType.MARKET;
                        }
                        if (update.id != null && !update.id.equals("0")) {
                            accountStatus.getNotificationQueue().add(new TradeNotification(
                                    update.id, update.order_id, accountConfig.getName(), name, update.symbol,
                                    side, type, update.price, update.size, update.price * update.size,
                                    update.feeSymbol, update.fee, update.time));
                            accountStatus.getNotificationQueue().add(new OrderNotification(
                                    update.order_id, name, update.symbol, accountConfig.getName(),
                                    side, type, update.orderSize, update.orderPrice, update.filledSize));
                        }
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
        @JsonProperty("X")
        public String state;
        @JsonProperty("x")
        public String executeType;
        @JsonProperty("z")
        public double filledSize;
        @JsonProperty("q")
        public double orderSize;
        @JsonProperty("ap")
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