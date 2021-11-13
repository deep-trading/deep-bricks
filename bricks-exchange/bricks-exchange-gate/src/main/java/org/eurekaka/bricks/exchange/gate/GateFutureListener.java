package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.Comparator;
import java.util.TreeMap;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class GateFutureListener extends WebSocketListener<FutureAccountStatus, GateFutureApi> {

    private final ObjectReader reader;

    public GateFutureListener(AccountConfig accountConfig,
                              FutureAccountStatus accountStatus,
                              GateFutureApi api) {
        super(accountConfig, accountStatus, api);

        reader = Utils.mapper.reader(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .forType(GateWebSocketResponse.class);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        GateWebSocketResponse resp = reader.readValue(message);
        if ("futures.pong".equals(resp.channel)) {
            accountStatus.updateLastPongTime();
        } else if ("futures.tickers".equals(resp.channel) && "update".equals(resp.event)) {
            for (GateWebSocketResult result : resp.result) {
                long price = Math.round(result.mark_price * PRECISION);
                if (price > 0) {
                    NetValue value = new NetValue(result.contract, accountConfig.getName(), price);
                    accountStatus.getNetValues().put(result.contract, value);
                }
                accountStatus.getFundingRates().put(result.contract, result.funding_rate);
            }
        } else if ("futures.order_book".equals(resp.channel)) {
            if ("all".equals(resp.event)) {
                for (GateWebSocketResult result : resp.result) {
//                        if (!result.bids.isEmpty()) {
//                            bidPrices.put(result.contract, result.bids.get(result.bids.size() - 1).price);
//                        }
//                        if (!result.asks.isEmpty()) {
//                            askPrices.put(result.contract, result.asks.get(0).price);
//                        }

                    TreeMap<Double, Double> bidOrderBook = new TreeMap<>(Comparator.reverseOrder());
                    for (GateWebSocketOrderBook bid : result.bids) {
                        bidOrderBook.put(bid.price, api.getSize(result.contract, bid.size));
                    }
                    accountStatus.getBidOrderBooks().put(result.contract, bidOrderBook);

                    TreeMap<Double, Double> askOrderBook = new TreeMap<>();
                    for (GateWebSocketOrderBook ask : result.asks) {
                        askOrderBook.put(ask.price, api.getSize(result.contract, ask.size));
                    }
                    accountStatus.getAskOrderBooks().put(result.contract, askOrderBook);
                }

            } else if ("update".equals(resp.event)) {
                for (GateWebSocketResult result : resp.result) {
                    if (result.book_size > 0) {
                        if (accountStatus.getBidOrderBooks().containsKey(result.book_contract)) {
                            accountStatus.getBidOrderBooks().get(result.book_contract).put(result.book_price,
                                    api.getSize(result.book_contract, result.book_size));
                        }
//                            sendLastPrice(new ExLastPrice(name,
//                                    result.book_contract, "bid", result.book_price));
                    } else if (result.book_size < 0) {
                        if (accountStatus.getAskOrderBooks().containsKey(result.book_contract)) {
                            accountStatus.getAskOrderBooks().get(result.book_contract).put(result.book_price,
                                    api.getSize(result.book_contract, -result.book_size));
                        }
//                            sendLastPrice(new ExLastPrice(name,
//                                    result.book_contract, "ask", result.book_price));
                    } else {
                        // 0 不知道是bid还是ask
                        if (accountStatus.getBidOrderBooks().containsKey(result.book_contract)) {
                            accountStatus.getBidOrderBooks().get(result.book_contract).remove(result.book_price);
                        }
                        if (accountStatus.getAskOrderBooks().containsKey(result.book_contract)) {
                            accountStatus.getAskOrderBooks().get(result.book_contract).remove(result.book_price);
                        }
                    }
                }
            }
        } else if ("futures.usertrades".equals(resp.channel) && "update".equals(resp.event)) {
            for (GateWebSocketResult result : resp.result) {
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
                    TradeNotification notification = new TradeNotification(result.id, result.order_id,
                            accountConfig.getName(), name, result.contract, side, type,
                            result.price, size, result.price * size,
                            "USDT", result.price * size * rate, result.create_time_ms);
                    accountStatus.getNotificationQueue().add(notification);
                }
            }
        } else if ("futures.orders".equals(resp.channel) && "update".equals(resp.event)) {
            for (GateWebSocketResult result : resp.result) {
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
                    if ("maker".equals(result.role)) {
                        type = OrderType.LIMIT;
                    }
                    double leftSize = api.getSize(result.contract, result.left);
                    OrderNotification notification = new OrderNotification(result.id, name,
                            result.contract, accountConfig.getName(), side, type,
                            size, result.price, size - Math.abs(leftSize));
                    accountStatus.getNotificationQueue().add(notification);
                }
            }
        } else if ("futures.positions".equals(resp.channel) && "update".equals(resp.event)) {
            for (GateWebSocketResult result : resp.result) {
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
