package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.model.AsyncStateOrder;
import org.eurekaka.bricks.server.model.OrderState;
import org.eurekaka.bricks.server.strategy.StopOrderTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * 使用异步接口，实现双向套利对冲
 * 基于Strategy06优化
 */
public class Strategy07 implements Strategy {
    private final static Logger logger = LoggerFactory.getLogger(Strategy07.class);

    private final BrickContext brickContext;
    private final StrategyConfig strategyConfig;

    private final AccountActor accountActor;

    private final StopOrderTracker orderTracker;

    private Info0 info1;
    private Info0 info2;

    // 盘口订单，Market 1与Market 2
    private volatile AsyncStateOrder bidOrder1;
    private volatile AsyncStateOrder askOrder1;

    private volatile AsyncStateOrder bidOrder2;
    private volatile AsyncStateOrder askOrder2;

    private volatile long posQuantity1;
    private volatile long posQuantity2;
    // account + bid/ask -> current time
    private final Map<String, Long> lastOrderTimeMap;

    private volatile long timeCounter;

    private int maxPositionQuantity;
    private int minOrderQuantity;

    private double sizePrecision;

    private int orderIndex1;
    private int orderIndex2;
    private final Map<String, Double> filledOrderSize;

    private boolean isDirect;
    private double orderProfitRate;

    private int timeCounterInterval;

    public Strategy07(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.accountActor = new AccountActor(brickContext.getAccountManager());

        orderTracker = new StopOrderTracker(strategyConfig, accountActor);

        this.orderIndex1 = 0;
        this.orderIndex2 = 0;

        lastOrderTimeMap = new ConcurrentHashMap<>();

        filledOrderSize = new HashMap<>();
    }


    @Override
    public void start() throws StrategyException {
        List<Info0> infos = brickContext.getInfoByName(strategyConfig.getInfoName());
        if (infos.size() != 2) {
            throw new StrategyException("it requires two infos: " + infos);
        }
        info1 = infos.get(0);
        info2 = infos.get(1);

        sizePrecision = Math.min(info1.getSizePrecision(), info2.getSizePrecision());

        maxPositionQuantity = strategyConfig.getInt("max_position_quantity", 1000);

        // 最小订单金额
        minOrderQuantity = strategyConfig.getInt("min_order_quantity", 13);

        isDirect = strategyConfig.getInt("order_alive_time", 0) == 0 &&
                strategyConfig.getDouble("order_risk_rate", 0D) == 0;

        orderProfitRate = strategyConfig.getDouble("order_profit_rate", 0.0003);

        posQuantity1 = accountActor.getPosition(info1).getQuantity();
        posQuantity2 = accountActor.getPosition(info2).getQuantity();

        timeCounter = System.currentTimeMillis();
        timeCounterInterval = strategyConfig.getInt("time_counter_interval", 180000);

        // 初始化订单跟踪器
        List<CurrentOrder> trackingOrders = new ArrayList<>();
        accountActor.asyncGetCurrentOrders(info1).thenAccept(currentOrders -> {
            for (CurrentOrder currentOrder : currentOrders) {
                if (currentOrder.getClientOrderId() != null) {
                    if (currentOrder.getClientOrderId().startsWith("_")) {
                        // 取消该订单
                        try {
                            accountActor.asyncCancelOrder(currentOrder.getAccount(),
                                    currentOrder.getName(), currentOrder.getSymbol(), currentOrder.getClientOrderId());
                        } catch (StrategyException e) {
                            throw new CompletionException("failed to init cancel order: " + currentOrder, e);
                        }
                    } else {
                        trackingOrders.add(currentOrder);
                    }
                }
            }
        });

        accountActor.asyncGetCurrentOrders(info2).thenAccept(currentOrders -> {
            for (CurrentOrder currentOrder : currentOrders) {
                if (currentOrder.getClientOrderId() != null) {
                    if (currentOrder.getClientOrderId().startsWith("_")) {
                        // 取消该订单
                        try {
                            accountActor.asyncCancelOrder(currentOrder.getAccount(),
                                    currentOrder.getName(), currentOrder.getSymbol(), currentOrder.getClientOrderId());
                        } catch (StrategyException e) {
                            throw new CompletionException("failed to init cancel order: " + currentOrder, e);
                        }
                    } else {
                        trackingOrders.add(currentOrder);
                    }
                }
            }
        });

        orderTracker.init(trackingOrders);

        lastOrderTimeMap.put(info1.getAccount() + OrderSide.BUY, System.currentTimeMillis());
        lastOrderTimeMap.put(info1.getAccount() + OrderSide.SELL, System.currentTimeMillis());
        lastOrderTimeMap.put(info2.getAccount() + OrderSide.BUY, System.currentTimeMillis());
        lastOrderTimeMap.put(info2.getAccount() + OrderSide.SELL, System.currentTimeMillis());
    }

    @Override
    public void stop() throws StrategyException {
        try {
            CompletableFuture.allOf(cancelOrder(bidOrder1),
                    cancelOrder(askOrder1),
                    cancelOrder(bidOrder2),
                    cancelOrder(askOrder2)).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.info("failed to stop strategy", e);
        }
    }

    @Override
    public void run() throws StrategyException {
        if (!brickContext.getAccount(info1.getAccount()).isAlive() ||
                !brickContext.getAccount(info2.getAccount()).isAlive()) {
            logger.warn("account dead, waiting...");
            try {
                CompletableFuture.allOf(cancelOrder(bidOrder1),
                        cancelOrder(askOrder1),
                        cancelOrder(bidOrder2),
                        cancelOrder(askOrder2)).get();
            } catch (InterruptedException | ExecutionException e) {
                logger.info("failed to cancel all orders", e);
            }
            return;
        }

        // 重置时间计数器
        if (System.currentTimeMillis() - timeCounter > timeCounterInterval) {
            timeCounter = System.currentTimeMillis();
            // log internal state here
            logger.info("position 1: {}, position 2: {}", posQuantity1, posQuantity2);
            logger.info("bid order1: {}", bidOrder1);
            logger.info("ask order1: {}", askOrder1);
            logger.info("bid order2: {}", bidOrder2);
            logger.info("ask order2: {}", askOrder2);
        }


        // 检查当前的对冲订单
        orderTracker.track();

        posQuantity1 = accountActor.getPosition(info1).getQuantity();
        posQuantity2 = accountActor.getPosition(info2).getQuantity();

        AsyncStateOrder o1 = updateOrder(info1, info2, OrderSide.BUY, bidOrder1, posQuantity1);
        if (o1 != null) {
            bidOrder1 = o1;
        }

        AsyncStateOrder o2 = updateOrder(info1, info2, OrderSide.SELL, askOrder1, posQuantity1);
        if (o2 != null) {
            askOrder1 = o2;
        }

        AsyncStateOrder o3 = updateOrder(info2, info1, OrderSide.BUY, bidOrder2, posQuantity2);
        if (o3 != null) {
            bidOrder2 = o3;
        }

        AsyncStateOrder o4 = updateOrder(info2, info1, OrderSide.SELL, askOrder2, posQuantity2);
        if (o4 != null) {
            askOrder2 = o4;
        }

    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        if (notification instanceof OrderNotification) {
            // 避免锁竞争，每次清理order size map
            filledOrderSize.entrySet().removeIf(e ->
                    (bidOrder1 == null || !e.getKey().equals(bidOrder1.getClientOrderId())) &&
                            (askOrder1 == null || !e.getKey().equals(askOrder1.getClientOrderId())) &&
                            (bidOrder2 == null || !e.getKey().equals(bidOrder2.getClientOrderId())) &&
                            (askOrder2 == null || !e.getKey().equals(askOrder2.getClientOrderId())));

            OrderNotification orderNotify = (OrderNotification) notification;
            if (orderNotify.getFilledSize() > 0) {
                logger.info("{}: received order notify: {}", System.currentTimeMillis() - timeCounter, orderNotify);
                // 确认成交订单来自于当前四个挂单
                Order order = null;
                if (bidOrder1 != null && orderNotify.getClientOrderId().equals(bidOrder1.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info1, info2, bidOrder1.getState());
                } else if (askOrder1 != null && orderNotify.getClientOrderId().equals(askOrder1.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info1, info2, askOrder1.getState());
                } else if (bidOrder2 != null && orderNotify.getClientOrderId().equals(bidOrder2.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info2, info1, bidOrder2.getState());
                } else if (askOrder2 != null && orderNotify.getClientOrderId().equals(askOrder2.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info2, info1, askOrder2.getState());
                }
                if (order != null) {
                    long startTime = System.currentTimeMillis();
                    orderTracker.submit(order).thenAccept(o -> {
                        long currentTime = System.currentTimeMillis();
                        logger.info("{}: {} made trade hedging order: {}",
                                currentTime - timeCounter, currentTime - startTime, o);
                    });
                }
            }
        } else if (notification instanceof TopDepthNotification) {
            // 当对应最优价格有变动时推送消息处理，走低或者走高
            TopDepthNotification depthPrice = (TopDepthNotification) notification;
            if (info1.getAccount().equals(depthPrice.getAccount())) {
                if (TopDepthNotification.DepthSide.BID.equals(depthPrice.getSide())) {
                    cancelOrder(info2, info1, bidOrder2, depthPrice.getTopPrice());
                } else if (TopDepthNotification.DepthSide.ASK.equals(depthPrice.getSide())) {
                    cancelOrder(info2, info1, askOrder2, depthPrice.getTopPrice());
                }
            } else if (info2.getAccount().equals(depthPrice.getAccount())) {
                if (TopDepthNotification.DepthSide.BID.equals(depthPrice.getSide())) {
                    cancelOrder(info1, info2, bidOrder1, depthPrice.getTopPrice());
                } else if (TopDepthNotification.DepthSide.ASK.equals(depthPrice.getSide())) {
                    cancelOrder(info1, info2, askOrder1, depthPrice.getTopPrice());
                }
            }
        }
    }

    private Order generateMarketHedgingOrder(OrderNotification orderNotify, Info0 info, Info0 other,
                                             OrderState orderState) {
        double sizeDiff = orderNotify.getFilledSize() -
                filledOrderSize.getOrDefault(orderNotify.getClientOrderId(), 0D);
        if (sizeDiff > 0) {
            logger.info("{}: generate market hedging order 0: ", System.currentTimeMillis() - timeCounter);
            long quantity = Math.round(sizeDiff * orderNotify.getPrice());
            if (quantity < minOrderQuantity) {
                logger.info("too small order filled size diff: {}, order: {}", sizeDiff, orderNotify);
                return null;
            }
            if (orderNotify.getSize() > orderNotify.getFilledSize()) {
                filledOrderSize.put(orderNotify.getClientOrderId(), orderNotify.getFilledSize());
            }
            logger.info("{}: generate market hedging order 1: ", System.currentTimeMillis() - timeCounter);

            // 记录所有成交信息
            OrderSide side = OrderSide.BUY;
            if (orderNotify.getSide().equals(OrderSide.BUY)) {
                // 下卖单对冲
                side = OrderSide.SELL;
            }
            double size = Utils.round(sizeDiff, sizePrecision);

            orderIndex2 = (orderIndex2 + 1) % 1000;
            String clientOrderId = orderNotify.getClientOrderId().substring(1) + "_" + orderIndex2;
            double price = orderNotify.getPrice();
            if (OrderSide.BUY.equals(orderNotify.getSide())) {
                // 高价卖
                price = price / (1 + accountActor.getCurrencyRate(info.getAccount()));
                price = price * (1 + accountActor.getMakerRate(orderNotify.getAccount()));
                price = price * (1 + accountActor.getTakerRate(other.getAccount()));
                price = price * (1 + orderProfitRate);
                price = price * (1 + accountActor.getCurrencyRate(other.getAccount()));

                price = Utils.floor(price, info.getPricePrecision());
            } else {
                price = price / (1 + accountActor.getCurrencyRate(info.getAccount()));
                price = price * (1 - accountActor.getMakerRate(orderNotify.getAccount()));
                price = price * (1 - accountActor.getTakerRate(other.getAccount()));
                price = price * (1 - orderProfitRate);
                price = price * (1 + accountActor.getCurrencyRate(other.getAccount()));

                price = Utils.ceil(price, info.getPricePrecision());
            }

            logger.info("{}: generate market hedging order 2: ", System.currentTimeMillis() - timeCounter);

            OrderType orderType = OrderType.LIMIT_GTC;
            // 订单已经撤销或正在撤销时，直接市价单对冲
            if (isDirect || OrderState.CANCELLING.equals(orderState) ||
                    OrderState.CANCELLED.equals(orderState)) {
                orderType = OrderType.MARKET;
            }

            return new Order(other.getAccount(), other.getName(), other.getSymbol(),
                    side, orderType, size, price, quantity, clientOrderId);
        }

        return null;
    }

    private AsyncStateOrder updateOrder(Info0 info, Info0 other, OrderSide side,
                              AsyncStateOrder currentOrder, long posQuantity) throws StrategyException {
        if (currentOrder != null && (currentOrder.getState().equals(OrderState.CANCELLING))) {
            return null;
        }

        // 生成推荐订单，order id = null
        AsyncStateOrder order = generateBaseOrder(info, other, side, posQuantity);

        // 始终保证第一时间取消价格错误的订单
        // 异步取消订单，等待下个周期再下单
        if (currentOrder != null && (currentOrder.getState().equals(OrderState.SUBMITTED) ||
                currentOrder.getState().equals(OrderState.SUBMITTING))) {
            // 检查是否需要撤单
            if (side.equals(OrderSide.BUY)) {
                double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
                if (currentOrder.getPrice() > order.getPrice() ||
                        currentOrder.getPrice() < order.getPrice() * (1 - baseOrderCancelRate)) {
                    currentOrder.setState(OrderState.CANCELLING);
                    long startCancelTime = System.currentTimeMillis();
                    accountActor.asyncCancelOrder(currentOrder).thenAccept(cancelled -> {
                        if (cancelled) {
                            currentOrder.setState(OrderState.CANCELLED);
                            long currentTime = System.currentTimeMillis();
                            logger.info("{}: {}: loop cancelled bid order: {}",
                                    currentTime - timeCounter, currentTime - startCancelTime, currentOrder);
                        } else {
                            // 若失败则恢复，等待下次撤单
                            currentOrder.setState(OrderState.SUBMITTED);
                        }
                    });
                    // 控制撤单后，一定时间内不再下单
                    lastOrderTimeMap.put(info.getAccount() + side, System.currentTimeMillis());
                    return null;
                }
            } else {
                double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
                if (currentOrder.getPrice() < order.getPrice() ||
                        currentOrder.getPrice() > order.getPrice() * (1 + baseOrderCancelRate)) {
                    currentOrder.setState(OrderState.CANCELLING);
                    long startCancelTime = System.currentTimeMillis();
                    accountActor.asyncCancelOrder(currentOrder).thenAccept(cancelled -> {
                        if (cancelled) {
                            currentOrder.setState(OrderState.CANCELLED);
                            long currentTime = System.currentTimeMillis();
                            logger.info("{}: {}: loop cancelled ask order: {}",
                                    currentTime - timeCounter, currentTime - startCancelTime, currentOrder);
                        } else {
                            currentOrder.setState(OrderState.SUBMITTED);
                        }
                    });
                    lastOrderTimeMap.put(info.getAccount() + side, System.currentTimeMillis());
                    return null;
                }
            }
        }

        // todo:: 检查订单是否满足下单条件，足够余额，价格范围等
        if (order.getQuantity() < minOrderQuantity) {
            return null;
        }

        // 若是可以下单
        if (currentOrder == null || currentOrder.getState().equals(OrderState.CANCELLED)) {
            if (checkOrderInterval(info.getAccount() + side)) {
                orderIndex1 = (orderIndex1 + 1) % 1000;
                String clientOrderId = "_" + info.getName() + "_" + System.currentTimeMillis() / 60000 + "_" + orderIndex1;
                order.setClientOrderId(clientOrderId);

                long startTime = System.currentTimeMillis();
                accountActor.asyncMakeOrder(order).thenAccept(newOrder -> {
                    if (newOrder == null || OrderStatus.EXPIRED.equals(newOrder.getStatus()) ||
                            OrderStatus.CANCELLED.equals(newOrder.getStatus())) {
                        // 此时订单失效/取消，可以直接设置本地订单状态为cancelled
                        order.setState(OrderState.CANCELLED);
                    } else {
                        long currentTime = System.currentTimeMillis();
                        if (order.getState().equals(OrderState.SUBMITTING)) {
                            order.setState(OrderState.SUBMITTED);
                            logger.info("{}: {} made new order: {}",
                                    currentTime - timeCounter, currentTime - startTime, newOrder);
                        } else if (order.getState().equals(OrderState.CANCELLING) ||
                                order.getState().equals(OrderState.CANCELLED)) {
                            // 状态为cancelled订单，也可能还未真正取消
                            // 再次取消订单，保证订单撤销
                            logger.info("{}: {} cancelling submitted order: {}",
                                    currentTime - timeCounter, currentTime - startTime, newOrder);
                            try {
                                accountActor.asyncCancelOrder(order).thenAccept(cancelled -> {
                                    order.setState(OrderState.CANCELLED);
                                });
                            } catch (StrategyException e) {
                                logger.error("{}: failed to cancel submitted order: {}",
                                        System.currentTimeMillis() - timeCounter, newOrder);
                            }
                        }
                    }
                });

                return order;
            }
        }

        return null;
    }

    private AsyncStateOrder generateBaseOrder(Info0 info, Info0 other, OrderSide side,
                                              long posQuantity) throws StrategyException {
        if (!side.equals(OrderSide.BUY) && !side.equals(OrderSide.SELL)) {
            throw new StrategyException("side error: " + side);
        }

        long orderQuantity = strategyConfig.getInt("order_quantity", 100);
        if (strategyConfig.getBoolean("rand_order_quantity", true)) {
            orderQuantity += System.nanoTime() % orderQuantity;
        }
        if (side.equals(OrderSide.BUY) && posQuantity < -orderQuantity) {
            orderQuantity = -posQuantity;
        } else if (side.equals(OrderSide.SELL) && posQuantity > orderQuantity) {
            orderQuantity = posQuantity;
        }
        if (side.equals(OrderSide.BUY) && posQuantity >= maxPositionQuantity ||
                side.equals(OrderSide.SELL) && posQuantity <= -maxPositionQuantity) {
            // 超出最大仓位限制，不再下单
            orderQuantity = 0;
        }

        AsyncStateOrder order = null;
        if (side.equals(OrderSide.BUY)) {
            DepthPrice depthPrice = accountActor.getBidDepthPrice(other.getAccount(),
                    other.getName(), other.getSymbol(), (int) orderQuantity);
            // 挂单，买一价价差配置
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            double price = depthPrice.price;

            // 参考价格转通用货币计算
            price = price / (1 + accountActor.getCurrencyRate(other.getAccount()));
            price = price * (1 - bidPriceRate);
            price = price * (1 - accountActor.getTakerRate(other.getAccount()));
            price = price * (1 - accountActor.getMakerRate(info.getAccount()));
            price = price * (1 + accountActor.getCurrencyRate(info.getAccount()));

            price = Utils.floor(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new AsyncStateOrder(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT_GTC, size, price, orderQuantity, OrderState.SUBMITTING);
        } else {
            DepthPrice depthPrice = accountActor.getAskDepthPrice(other.getAccount(),
                    other.getName(), other.getSymbol(), (int) orderQuantity);
            // 挂单，卖一价价差配置
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = depthPrice.price;

            // 允许挂卖单
            price = price / (1 + accountActor.getCurrencyRate(other.getAccount()));
            price = price * (1 + askPriceRate);
            price = price * (1 + accountActor.getTakerRate(other.getAccount()));
            price = price * (1 + accountActor.getMakerRate(info.getAccount()));
            price = price * (1 + accountActor.getCurrencyRate(info.getAccount()));
            price = Utils.ceil(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new AsyncStateOrder(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT_GTC, size, price, orderQuantity, OrderState.SUBMITTING);
        }

        return order;
    }

    private void cancelOrder(Info0 info, Info0 other, AsyncStateOrder currentOrder,
                             double topPrice) throws StrategyException {
        if (currentOrder == null || currentOrder.getState().equals(OrderState.CANCELLING) ||
                currentOrder.getState().equals(OrderState.CANCELLED)) {
            return;
        }

        // 检查是否需要撤单
        if (currentOrder.getSide().equals(OrderSide.BUY)) {
            // 根据top price，计算当前合理价格
            double price = topPrice;
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            // 参考价格转通用货币计算
            price = price / (1 + accountActor.getCurrencyRate(other.getAccount()));
            price = price * (1 - bidPriceRate);
            price = price * (1 - accountActor.getTakerRate(other.getAccount()));
            price = price * (1 - accountActor.getMakerRate(info.getAccount()));
            price = price * (1 + accountActor.getCurrencyRate(info.getAccount()));

            price = Utils.floor(price, info.getPricePrecision());

            double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
            if (currentOrder.getPrice() > price ||
                    currentOrder.getPrice() < price * (1 - baseOrderCancelRate)) {
                currentOrder.setState(OrderState.CANCELLING);
                long startCancelTime = System.currentTimeMillis();
                accountActor.asyncCancelOrder(currentOrder).thenAccept(cancelled -> {
                    if (cancelled) {
                        currentOrder.setState(OrderState.CANCELLED);
                        long currentTime = System.currentTimeMillis();
                        logger.info("{}: {}: cancelled bid order: {}",
                                currentTime - timeCounter, currentTime - startCancelTime, currentOrder);
                    } else {
                        currentOrder.setState(OrderState.SUBMITTED);
                    }
                });
                // 控制撤单后，一定时间内不再下单
                lastOrderTimeMap.put(info.getAccount() + OrderSide.BUY, System.currentTimeMillis());
            }
        } else {
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = topPrice;

            // 允许挂卖单
            price = price / (1 + accountActor.getCurrencyRate(other.getAccount()));
            price = price * (1 + askPriceRate);
            price = price * (1 + accountActor.getTakerRate(other.getAccount()));
            price = price * (1 + accountActor.getMakerRate(info.getAccount()));
            price = price * (1 + accountActor.getCurrencyRate(info.getAccount()));
            price = Utils.ceil(price, info.getPricePrecision());

            double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
            if (currentOrder.getPrice() < price ||
                    currentOrder.getPrice() > price * (1 + baseOrderCancelRate)) {
                currentOrder.setState(OrderState.CANCELLING);
                long startCancelTime = System.currentTimeMillis();
                accountActor.asyncCancelOrder(currentOrder).thenAccept(cancelled -> {
                    if (cancelled) {
                        currentOrder.setState(OrderState.CANCELLED);
                        long currentTime = System.currentTimeMillis();
                        logger.info("{}: {}: cancelled ask order: {}",
                                currentTime - timeCounter, currentTime - startCancelTime, currentOrder);
                    } else {
                        currentOrder.setState(OrderState.SUBMITTED);
                    }
                });
                lastOrderTimeMap.put(info.getAccount() + OrderSide.SELL, System.currentTimeMillis());
            }
        }

    }



    private CompletableFuture<Void> cancelOrder(AsyncStateOrder order) throws StrategyException {
        if (order != null && OrderState.SUBMITTED.equals(order.getState())) {
            order.setState(OrderState.CANCELLING);
            return accountActor.asyncCancelOrder(order).thenAccept(unused -> order.setState(OrderState.CANCELLED));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 防止下单频率过高，在短时间内禁止下单
     * @param key account name + side
     * @return 是否允许下单
     */
    private boolean checkOrderInterval(String key) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastOrderTimeMap.get(key) < strategyConfig.getInt("order_interval", 1000)) {
            return false;
        }
        lastOrderTimeMap.put(key, currentTime);
        return true;
    }
}
