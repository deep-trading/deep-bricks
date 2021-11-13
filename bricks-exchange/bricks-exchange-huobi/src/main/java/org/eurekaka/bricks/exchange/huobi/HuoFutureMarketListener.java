package org.eurekaka.bricks.exchange.huobi;

import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.FutureAccountStatus;
import org.eurekaka.bricks.common.model.NetValue;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class HuoFutureMarketListener extends WebSocketListener<FutureAccountStatus, HuoFutureApi> {
    public HuoFutureMarketListener(AccountConfig accountConfig,
                                  FutureAccountStatus accountStatus, HuoFutureApi api) {
        super(accountConfig, accountStatus, api);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        HuoFutureWebSocketV1 msg = Utils.mapper.readValue(message, HuoFutureWebSocketV1.class);
        if (msg.ping != 0) {
            webSocket.sendText(Utils.mapper.writeValueAsString(new HuoFuturePongV1(msg.ping)), true);
        } else if (msg.ch != null) {
            HuoFutureTopic topic = HuoFutureTopic.parseTopicV1(msg.ch);
            if ("depth".equals(topic.part2)) {
                if (!msg.tick.bids.isEmpty()) {
                    TreeMap<Double, Double> bidOrderBook = new TreeMap<>(Comparator.reverseOrder());
                    for (List<Double> bid : msg.tick.bids) {
                        bidOrderBook.put(bid.get(0), api.getSize(topic.part1, bid.get(1)));
                    }
                    accountStatus.getBidOrderBooks().put(topic.part1, bidOrderBook);
                }

                if (!msg.tick.asks.isEmpty()) {
                    TreeMap<Double, Double> askOrderBook = new TreeMap<>();
                    for (List<Double> ask : msg.tick.asks) {
                        askOrderBook.put(ask.get(0), api.getSize(topic.part1, ask.get(1)));
                    }
                    accountStatus.getAskOrderBooks().put(topic.part1, askOrderBook);
                }

                if (!msg.tick.bids.isEmpty() && !msg.tick.asks.isEmpty()) {
                    double bidPrice = msg.tick.bids.get(0).get(0);
                    double askPrice = msg.tick.asks.get(0).get(0);
                    double midPrice = (bidPrice + askPrice) / 2;
                    double diff = (midPrice - bidPrice) / midPrice;
                    if (diff < 0.03) {
                        // 误差小于 3%，则认为有效价格
                        long price = Math.round(midPrice * PRECISION);
                        accountStatus.getNetValues().put(topic.part1,
                                new NetValue(topic.part1, accountConfig.getName(), price));
                    }
                }
            }
        }
    }
}
