package org.eurekaka.bricks.exchange.huobi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.concurrent.Executor;

public class HuoFutureAccountListener extends WebSocketListener<FutureAccountStatus, HuoFutureApi> {
    private final ObjectReader reader;

    public HuoFutureAccountListener(AccountConfig accountConfig,
                                    FutureAccountStatus accountStatus,
                                    HuoFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);

        this.reader = Utils.mapper.reader().forType(HuoFutureWebSocketV2.class)
                .with(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        HuoFutureWebSocketV2 msg = this.reader.readValue(message);
        if ("ping".equals(msg.op)) {
            webSocket.sendText(Utils.mapper.writeValueAsString(new HuoFuturePongV2(msg.ts)), true);
        } else if ("notify".equals(msg.op)) {
//            logger.info("notify: {}", message);
            HuoFutureTopic topic = HuoFutureTopic.parseTopicV1(msg.topic);
            if ("orders_cross".equals(topic.part0)) {
                String symbol = msg.contract_code;
                String name = accountStatus.getSymbols().get(msg.contract_code);
                if (name != null && accountStatus.getNotificationQueue() != null) {
                    OrderSide side = OrderSide.NONE;
                    if ("buy".equals(msg.direction)) {
                        side = OrderSide.BUY;
                    } else if ("sell".equals(msg.direction)) {
                        side = OrderSide.SELL;
                    }

                    for (HuoFutureTrade trade : msg.trade) {
                        OrderType orderType = OrderType.NONE;
                        if ("taker".equals(trade.role)) {
                            orderType = OrderType.MARKET;
                        } else if ("maker".equals(trade.role)) {
                            orderType = OrderType.LIMIT;
                        }
                        double size = api.getSize(symbol, trade.trade_volume);
                        accountStatus.getNotificationQueue().add(new TradeNotification(trade.id,
                                msg.order_id_str, accountConfig.getName(), name, symbol,
                                side, orderType, trade.trade_price, size, trade.trade_turnover,
                                trade.fee_asset, trade.trade_fee, trade.created_at));
                    }
                }
            } else if ("positions_cross".equals(topic.part0)) {
                for (HuoFutureDataV1 data : msg.data) {
                    String symbol = data.contract_code;
                    double size = api.getSize(symbol, data.volume);
                    if ("sell".equals(data.direction)) {
                        size = -size;
                    }
                    long quantity = Math.round(data.last_price * size);

                    accountStatus.getPositionValues().put(symbol + data.direction, new PositionValue(symbol,
                            accountConfig.getName(), size, data.last_price, quantity, 0, 0));
                }
            } else if ("public".equals(topic.part0) && "funding_rate".equals(topic.part2)) {
                for (HuoFutureDataV1 data : msg.data) {
                    accountStatus.getFundingRates().put(data.contract_code, data.funding_rate);
                }
            }
        }
    }
}
