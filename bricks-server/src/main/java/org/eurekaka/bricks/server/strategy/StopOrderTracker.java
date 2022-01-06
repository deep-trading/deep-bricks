package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.OrderTracker;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

// todo:: 定期拿取当前订单，更新现有订单状态
public class StopOrderTracker implements OrderTracker {
    private final static Logger logger = LoggerFactory.getLogger(StopOrderTracker.class);

    private final Map<String, CurrentOrder> trackingOrderMap;
    private final Map<String, CurrentOrder> removedOrderMap;

    private final Map<String, CurrentOrder> expiredOrderMap;

    private final AccountActor accountActor;
    private final StrategyConfig strategyConfig;
    // milliseconds for order alive,
    // if 0, order is always alive until price risk triggerred
    private int orderAliveTime;
    // order risk price rate,
    // if 0, order is always alive until expired
    private double orderRiskRate;
    private int minOrderQuantity;

    private int index;
    private int timeCounterInterval;
    private volatile long timeCounter;

    public StopOrderTracker(StrategyConfig strategyConfig, AccountActor accountActor) {
        this.accountActor = accountActor;
        this.strategyConfig = strategyConfig;

        this.trackingOrderMap = new ConcurrentHashMap<>();
        this.removedOrderMap = new ConcurrentHashMap<>();
        this.expiredOrderMap = new ConcurrentHashMap<>();
    }

    @Override
    public void init(List<CurrentOrder> orders) throws StrategyException {
        orderAliveTime = strategyConfig.getInt("order_alive_time", 0);
        orderRiskRate = strategyConfig.getDouble("order_risk_rate", 0D);
        minOrderQuantity = strategyConfig.getInt("min_order_quantity", 13);

        // 加载当前未完成的跟踪订单，启动时从交易所获取
        for (CurrentOrder order : orders) {
            // 从当前订单恢复
            trackingOrderMap.put(order.getClientOrderId(), order);
        }

        index = 0;

        timeCounter = System.currentTimeMillis();
        timeCounterInterval = strategyConfig.getInt("time_counter_interval", 180000);
    }

    @Override
    public void track() throws StrategyException {
        for (CurrentOrder order : trackingOrderMap.values()) {
            // 根据价格检查是否需要移除订单
            // 处理风险订单
            if (OrderSide.BUY.equals(order.getSide())) {
                // 买单，查看当前卖一价，是否触发风险价格
                double topPrice = accountActor.getAskTopPrice(order.getAccount(),
                        order.getName(), order.getSymbol());
                if (topPrice != 0 && orderRiskRate > 0) {
                    if (topPrice > order.getPrice() * (1 + orderRiskRate)) {
                        logger.info("bid order risk too high, order price: {}, ask price: {}",
                                order.getPrice(), topPrice);
                        expiredOrderMap.put(order.getClientOrderId(), order);
                        continue;
                    } else if (topPrice < order.getPrice()) {
                        logger.info("bid order should be filled, id: {}, ask price {} is lower than order price {}",
                                order.getClientOrderId(), topPrice, order.getPrice());
                        removedOrderMap.put(order.getClientOrderId(), order);
                        continue;
                    }
                }
            } else if (OrderSide.SELL.equals(order.getSide())) {
                double topPrice = accountActor.getBidTopPrice(order.getAccount(),
                        order.getName(), order.getSymbol());
                if (topPrice != 0 && orderRiskRate > 0) {
                    if (topPrice < order.getPrice() * (1 - orderRiskRate)) {
                        logger.info("ask order risk too high, id: {}, order price: {}, bid price: {}",
                                order.getClientOrderId(), order.getPrice(), topPrice);
                        expiredOrderMap.put(order.getClientOrderId(), order);
                        continue;
                    } else if (topPrice > order.getPrice()) {
                        logger.info("ask order should be filled, bid price {} is higher than order price {}",
                                topPrice, order.getPrice());
                        removedOrderMap.put(order.getClientOrderId(), order);
                        continue;
                    }
                }
            }

            // 处理超时订单
            if (orderAliveTime > 0 && order.getTime() + orderAliveTime < System.currentTimeMillis()) {
                // 取消挂单，转市价单对冲
                logger.info("order expired: {}", order);
                expiredOrderMap.put(order.getClientOrderId(), order);
            }
        }

        for (String clientOrderId : removedOrderMap.keySet()) {
            trackingOrderMap.remove(clientOrderId);
        }
        // 针对过期订单，先
        for (CurrentOrder currentOrder : expiredOrderMap.values()) {
            trackingOrderMap.remove(currentOrder.getClientOrderId());

            completeCurrentOrder(currentOrder);
        }

        if (System.currentTimeMillis() - timeCounter > timeCounterInterval) {
            timeCounter = System.currentTimeMillis();
            // log internal state here
            logger.info("current tracking orders: {}", trackingOrderMap.size());
            for (CurrentOrder currentOrder : trackingOrderMap.values()) {
                logger.info("current tracking order: {}", currentOrder);
            }
        }

        removedOrderMap.clear();
        expiredOrderMap.clear();
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

    private CompletableFuture<Void> completeCurrentOrder(CurrentOrder order) throws StrategyException {
        return accountActor.asyncCancelOrder(order.getAccount(), order.getName(),
                        order.getSymbol(), order.getClientOrderId())
                .thenCompose(unused -> {
                    try {
                        return accountActor.asyncGetOrder(order.getAccount(),
                                order.getName(), order.getSymbol(), order.getClientOrderId());
                    } catch (StrategyException e) {
                        throw new CompletionException("failed to get order after cancelling", e);
                    }
                }).thenAccept(currentOrder -> {
                    if (currentOrder == null) {
                        logger.warn("order not existed: {}", order);
                        return;
                    }
                    if (OrderStatus.FILLED.equals(currentOrder.getStatus())) {
                        logger.info("current order has been filled: {}", currentOrder);
                        return;
                    }
                    double size = currentOrder.getSize() - currentOrder.getFilledSize();
                    long quantity = Math.round(size * currentOrder.getPrice());
                    if (quantity < minOrderQuantity) {
                        logger.info("making order ignored, quantity: {}", quantity);
                        return;
                    }
                    index = (index + 1) % 100000;
                    String clientOrderId = currentOrder.getClientOrderId() + "_" + index;
                    Order o = new Order(currentOrder.getAccount(), order.getName(), order.getSymbol(),
                            order.getSide(), OrderType.MARKET, size, order.getPrice(), quantity, clientOrderId);
                    try {
                        logger.info("making market order: {}", o);
                        accountActor.asyncMakeOrder(o);
                    } catch (StrategyException e) {
                        throw new CompletionException("failed to make tracking order: " + o, e);
                    }
                });
    }
}
