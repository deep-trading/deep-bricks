package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class FtxFutureListener extends WebSocketListener<FutureAccountStatus, FtxFutureApi> {

    public FtxFutureListener(AccountConfig accountConfig,
                             FutureAccountStatus accountStatus, FtxFutureApi api) {
        super(accountConfig, accountStatus, api);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        FtxWebSocketMsg msg = Utils.mapper.readValue(message, FtxWebSocketMsg.class);
        if (msg.data == null || msg.channel == null) {
            return;
        }
        if ("fills".equals(msg.channel) && msg.data.future != null) {
            System.out.println(message);
            String name = accountStatus.getSymbols().get(msg.data.future);
            if (name == null) {
                return;
            }

            OrderType type = OrderType.MARKET;
            if ("maker".equals(msg.data.liquidity)) {
                type = OrderType.LIMIT;
            }

            OrderSide side = OrderSide.NONE;
            if ("buy".equals(msg.data.side)) {
                side = OrderSide.BUY;
            } else if ("sell".equals(msg.data.side)) {
                side = OrderSide.SELL;
            }

            if (accountStatus.getNotificationQueue() != null) {
                // todo: client order id 不存在 fills 里
                accountStatus.getNotificationQueue().add(new TradeNotification(
                        msg.data.id, msg.data.clientId, msg.data.orderId, accountConfig.getName(), name,
                        msg.data.future, side, type, msg.data.price, msg.data.size,
                        msg.data.price * msg.data.size, "USD", msg.data.fee,
                        System.currentTimeMillis()));
            }

            // 更新positions
            PositionValue positionValue = accountStatus.getPositionValues().get(msg.data.future);
            if (positionValue != null) {
                double size = positionValue.getSize();
                if (side.equals(OrderSide.BUY)) {
                    size += msg.data.size;
                } else {
                    size -= msg.data.size;
                }

                double price = msg.data.price;
                long quantity = Math.round(size * price);

                double entryPrice = positionValue.getEntryPrice();
                double pnl = (price - entryPrice) * size;
                accountStatus.getPositionValues().put(positionValue.getSymbol(),
                        new PositionValue(name, positionValue.getSymbol(), accountConfig.getName(),
                                size, price, quantity, entryPrice, pnl, System.currentTimeMillis()));
            }
        } else if ("orders".equals(msg.channel) && msg.data.market != null) {
            System.out.println(message);
            String name = accountStatus.getSymbols().get(msg.data.market);
            if (name == null) {
                return;
            }

            OrderType type = OrderType.MARKET;
            if ("limit".equals(msg.data.type)) {
                type = OrderType.LIMIT;
            }

            OrderSide side = OrderSide.NONE;
            if ("buy".equals(msg.data.side)) {
                side = OrderSide.BUY;
            } else if ("sell".equals(msg.data.side)) {
                side = OrderSide.SELL;
            }

            if (accountStatus.getNotificationQueue() != null) {
                accountStatus.getNotificationQueue().add(new OrderNotification(
                        msg.data.id, name, msg.data.market, accountConfig.getName(),
                        side, type, msg.data.size, msg.data.avgFillPrice, msg.data.filledSize));
            }
        } else if ("ticker".equals(msg.channel)) {
            double median = 0;
            if (msg.data.bid > 0 && msg.data.last > 0 && msg.data.ask > 0) {
                if (msg.data.ask > msg.data.last && msg.data.last > msg.data.bid) {
                    median = msg.data.last;
                } else if (msg.data.last > msg.data.ask) {
                    median = msg.data.ask;
                } else if (msg.data.bid > msg.data.last) {
                    median = msg.data.bid;
                }
                if (median > 0) {
                    long price = Math.round(median * PRECISION);
                    accountStatus.getNetValues().put(msg.market,
                            new NetValue(msg.market, accountConfig.getName(), price));
                    if ("USDT-PERP".equals(msg.market)) {
                        accountStatus.setMarkUsdt(median);
                    }
                }
            }
        } else if ("orderbook".equals(msg.channel)) {
            if ("partial".equals(msg.data.action)) {
                TreeMap<Double, Double> bidOrderBook = new TreeMap<>(Comparator.reverseOrder());
                for (List<Double> bid : msg.data.bids) {
                    bidOrderBook.put(bid.get(0), bid.get(1));
                }
                accountStatus.getBidOrderBooks().put(msg.market, bidOrderBook);

                TreeMap<Double, Double> askOrderBook = new TreeMap<>();
                for (List<Double> ask : msg.data.asks) {
                    askOrderBook.put(ask.get(0), ask.get(1));
                }
                accountStatus.getAskOrderBooks().put(msg.market, askOrderBook);
            } else if ("update".equals(msg.data.action)) {
                for (List<Double> bid : msg.data.bids) {
                    if (accountStatus.getBidOrderBooks().containsKey(msg.market)) {
                        if (bid.get(1) == 0) {
                            accountStatus.getBidOrderBooks().get(msg.market).remove(bid.get(0));
                        } else {
                            accountStatus.getBidOrderBooks().get(msg.market).put(bid.get(0), bid.get(1));
                        }
                    }
                }
                for (List<Double> ask : msg.data.asks) {
                    if (accountStatus.getAskOrderBooks().containsKey(msg.market)) {
                        if (ask.get(1) == 0) {
                            accountStatus.getAskOrderBooks().get(msg.market).remove(ask.get(0));
                        } else {
                            accountStatus.getAskOrderBooks().get(msg.market).put(ask.get(0), ask.get(1));
                        }
                    }
                }
            }
        }
    }
}
