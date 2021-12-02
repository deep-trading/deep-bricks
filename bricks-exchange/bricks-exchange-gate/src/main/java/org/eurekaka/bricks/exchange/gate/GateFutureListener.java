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

    public GateFutureListener(AccountConfig accountConfig,
                              FutureAccountStatus accountStatus,
                              GateFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);

        reader = Utils.mapper.reader().forType(GateWebSocketResp.class);
        reader1 = Utils.mapper.reader(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .forType(new TypeReference<List<GateWebSocketResult>>() {});
        reader2 = Utils.mapper.reader().forType(GateWebSocketResultV2.class);
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
        } else if ("futures.order_book".equals(resp.channel)) {
            List<GateWebSocketResult> results = reader1.readValue(resp.result);
            if ("all".equals(resp.event)) {
//                System.out.println("all: " + resp.result);
                for (GateWebSocketResult result : results) {
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

                    TreeMap<Double, Double> askOrderBook = new TreeMap<>(Comparator.naturalOrder());
                    for (GateWebSocketOrderBook ask : result.asks) {
                        askOrderBook.put(ask.price, api.getSize(result.contract, ask.size));
                    }
                    accountStatus.getAskOrderBooks().put(result.contract, askOrderBook);
                }

            } else if ("update".equals(resp.event)) {
//                System.out.println("update: " + resp.result);
                for (GateWebSocketResult result : results) {
                    if (result.book_size > 0) {
                        Utils.updateOrderBookEntry(accountStatus.getBidOrderBooks(), result.book_contract,
                                result.book_price, api.getSize(result.book_contract, result.book_size));
                    } else if (result.book_size < 0) {
                        Utils.updateOrderBookEntry(accountStatus.getAskOrderBooks(), result.book_contract,
                                result.book_price, api.getSize(result.book_contract, -result.book_size));
//                            sendLastPrice(new ExLastPrice(name,
//                                    result.book_contract, "ask", result.book_price));
                    } else {
                        // 0 不知道是bid还是ask
                        Utils.removeOrderBookEntry(accountStatus.getBidOrderBooks(),
                                result.book_contract, result.book_price);
                        Utils.removeOrderBookEntry(accountStatus.getAskOrderBooks(),
                                result.book_contract, result.book_price);
                    }
                }
            }
        } else if ("futures.book_ticker".equals(resp.channel) && "update".equals(resp.event)) {
            GateWebSocketResultV2 result = reader2.readValue(resp.result);
//            System.out.println(resp.result);
            if (result.bidPrice > 0 && result.bidSize != 0) {
                Utils.updateOrderBookTicker(accountStatus.getBidOrderBooks(), result.contract,
                        result.bidPrice, api.getSize(result.contract, result.bidSize));
            }
            if (result.askPrice > 0 && result.askSize != 0) {
                Utils.updateOrderBookTicker(accountStatus.getAskOrderBooks(), result.contract,
                        result.askPrice, api.getSize(result.contract, result.askSize));
            }
        } else if ("futures.usertrades".equals(resp.channel) && "update".equals(resp.event)) {
//            System.out.println(message);
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
//            System.out.println(message);
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
