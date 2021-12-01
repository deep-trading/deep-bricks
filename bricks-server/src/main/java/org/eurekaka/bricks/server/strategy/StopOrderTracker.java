package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.OrderTracker;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class StopOrderTracker implements OrderTracker {
    private final Map<String, CurrentOrder> trackingOrderMap;

    private final AccountActor accountActor;
    private final StrategyConfig strategyConfig;
    // milliseconds for order alive,
    // if 0, order is always alive until price risk triggerred
    private int orderAliveTime;
    // order risk price rate,
    // if 0, order is always alive until expired
    private double orderRiskRate;

    public StopOrderTracker(StrategyConfig strategyConfig, AccountActor accountActor) {
        this.accountActor = accountActor;
        this.strategyConfig = strategyConfig;

        this.trackingOrderMap = new ConcurrentHashMap<>();
    }

    @Override
    public void init(List<CurrentOrder> orders) throws StrategyException {
        orderAliveTime = strategyConfig.getInt("order_alive_time", 0);
        orderRiskRate = strategyConfig.getDouble("order_risk_rate", 0D);

        // 加载当前未完成的跟踪订单，启动时从交易所获取
        for (CurrentOrder order : orders) {
            // 从当前订单恢复
            trackingOrderMap.put(order.getClientOrderId(), order);
        }
    }

    @Override
    public void track() throws StrategyException {
        for (CurrentOrder order : trackingOrderMap.values()) {
            // 处理超时订单

            if (orderAliveTime > 0 && order.getTime() + orderAliveTime > System.currentTimeMillis()) {
                // 取消挂单，转市价单对冲
                completeCurrentOrder(order);
            }

            if (orderRiskRate > 0D) {
                // 处理风险订单
                int quantity = (int) Math.round((order.getSize() - order.getFilledSize()) * order.getPrice());
                if (OrderSide.BUY.equals(order.getSide())) {
                    // 买单，查看当前卖一价，是否触发风险价格
                    DepthPrice depthPrice = accountActor.getAskDepthPrice(order.getAccount(),
                            order.getName(), order.getSymbol(), quantity);
                    if (depthPrice != null && depthPrice.price > order.getPrice() * (1 + orderRiskRate)) {
                        // 取消挂单，转市价单对冲
                        completeCurrentOrder(order);
                    }
                } else if (OrderSide.SELL.equals(order.getSide())) {
                    DepthPrice depthPrice = accountActor.getBidDepthPrice(order.getAccount(),
                            order.getName(), order.getSymbol(), quantity);
                    if (depthPrice != null && depthPrice.price < order.getPrice() * (1 - orderRiskRate)) {
                        // 取消挂单，转市价单对冲
                        completeCurrentOrder(order);
                    }
                }
            }
        }
    }

    /**
     * order type: LIMIT_GTC
     * @param order 待跟踪订单
     * @throws StrategyException 执行失败
     */
    @Override
    public CompletableFuture<CurrentOrder> submit(Order order) throws StrategyException {
        return accountActor.asyncMakeOrder(order).thenApply(currentOrder -> {
            if (order.getOrderType().equals(OrderType.LIMIT_GTC) &&
                    (orderAliveTime > 0 || orderRiskRate > 0)) {
                if (OrderStatus.NEW.equals(currentOrder.getStatus()) ||
                        OrderStatus.PART_FILLED.equals(currentOrder.getStatus())) {
                    trackingOrderMap.put(order.getClientOrderId(), currentOrder);
                }
            }
            return currentOrder;
        });
    }

    private void completeCurrentOrder(CurrentOrder order) throws StrategyException {
        accountActor.asyncCancelOrder(order.getAccount(), order.getName(), order.getSymbol(), order.getClientOrderId())
                .thenCompose(unused -> {
                    try {
                        return accountActor.asyncGetOrder(order.getAccount(),
                                order.getName(), order.getSymbol(), order.getClientOrderId());
                    } catch (StrategyException e) {
                        throw new CompletionException("failed to get order after cancelling", e);
                    }
                }).thenAccept(currentOrder -> {
                    double size = currentOrder.getSize() - currentOrder.getFilledSize();
                    long quantity = Math.round(size * currentOrder.getPrice());
                    String clientOrderId = currentOrder.getClientOrderId() + "_1";
                    Order o = new Order(currentOrder.getAccount(), order.getName(), order.getSymbol(),
                            order.getSide(), OrderType.MARKET, currentOrder.getSize(),
                            currentOrder.getPrice(), quantity, clientOrderId);
                    try {
                        accountActor.asyncMakeOrder(o);
                    } catch (StrategyException e) {
                        throw new CompletionException("failed to make tracking order: " + o, e);
                    }
                });
    }
}
