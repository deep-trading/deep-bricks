package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Executor;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class FtxFutureListener extends WebSocketListener<FutureAccountStatus, FtxFutureApi> {
    private final String currencyRateSymbol;

    public FtxFutureListener(AccountConfig accountConfig,
                             FutureAccountStatus accountStatus,
                             FtxFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);

        currencyRateSymbol = accountConfig.getProperty("currency_rate_symbol", "USDT/USD");
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        FtxWebSocketMsg msg = Utils.mapper.readValue(message, FtxWebSocketMsg.class);
//        System.out.println(message);
        if ("pong".equals(msg.type)) {
            accountStatus.updateLastPongTime();
            return;
        }

        FtxWebSocketData data = msg.data;
        if (data == null || msg.channel == null) {
            return;
        }
        if ("fills".equals(msg.channel) && data.future != null) {
            String name = accountStatus.getSymbols().get(data.future);
            if (name == null) {
                return;
            }

            OrderType type = OrderType.MARKET;
            if ("maker".equals(data.liquidity)) {
                type = OrderType.LIMIT;
            }

            OrderSide side = FtxUtils.getOrderSide(data.side);

            if (accountStatus.getNotificationQueue() != null) {
                // todo: client order id 不存在 fills 里
                accountStatus.getNotificationQueue().add(new TradeNotification(
                        data.id, data.clientId, data.orderId, accountConfig.getName(), name,
                        data.future, side, type, data.price, data.size,
                        data.price * data.size, "USD", data.fee,
                        FtxUtils.parseTimestampString(data.time)));
            }

            // 更新positions
            PositionValue positionValue = accountStatus.getPositionValues().get(data.future);
            if (positionValue != null) {
                double size = positionValue.getSize();
                if (side.equals(OrderSide.BUY)) {
                    size += data.size;
                } else {
                    size -= data.size;
                }

                double price = data.price;
                long quantity = Math.round(size * price);

                double entryPrice = positionValue.getEntryPrice();
                double pnl = (price - entryPrice) * size;
                accountStatus.getPositionValues().put(positionValue.getSymbol(),
                        new PositionValue(name, positionValue.getSymbol(), accountConfig.getName(),
                                size, price, quantity, entryPrice, pnl, System.currentTimeMillis()));
            }
        } else if ("orders".equals(msg.channel) && data.market != null) {
            String name = accountStatus.getSymbols().get(data.market);
            if (name == null) {
                return;
            }

            OrderType type = FtxUtils.getOrderType(data.type, data.ioc, data.postOnly);
            OrderSide side = FtxUtils.getOrderSide(data.side);

            if (accountStatus.getNotificationQueue() != null) {
                accountStatus.getNotificationQueue().add(new OrderNotification(
                        data.id, name, data.future, accountConfig.getName(),
                        side, type, data.size, data.price, data.filledSize,
                        data.avgFillPrice, data.clientId,
                        FtxUtils.getOrderStatus(data.status, data.size, data.filledSize),
                        System.currentTimeMillis()));
            }
        } else if ("ticker".equals(msg.channel)) {
            double median = 0;
            if (data.bid > 0 && data.last > 0 && data.ask > 0) {
                if (data.ask > data.last && data.last > data.bid) {
                    median = data.last;
                } else if (data.last > data.ask) {
                    median = data.ask;
                } else if (data.bid > data.last) {
                    median = data.bid;
                }
                long price = Math.round(median * PRECISION);
                accountStatus.getNetValues().put(msg.market,
                        new NetValue(msg.market, accountConfig.getName(), price));
                if (currencyRateSymbol.equals(msg.market)) {
                    accountStatus.setCurrencyRate(data.last - 1);
                }
                // 更新market
                accountStatus.updateBidOrderBookTicker(msg.market, data.bid, data.bidSize);
                accountStatus.updateBidOrderBookTicker(msg.market, data.ask, data.askSize);
            }
        } else if ("orderbook".equals(msg.channel)) {
            if ("partial".equals(data.action)) {
                accountStatus.buildOrderBook(msg.market, OrderBookValue.parsePairs(data.bids),
                        OrderBookValue.parsePairs(data.asks));
            } else if ("update".equals(data.action)) {
                accountStatus.updateOrderBook(msg.market, OrderBookValue.parsePairs(data.bids),
                        OrderBookValue.parsePairs(data.asks));
            }
        }
    }
}
