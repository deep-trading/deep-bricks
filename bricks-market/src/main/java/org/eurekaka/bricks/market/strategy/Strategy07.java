package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.model.AsyncStateOrder;
import org.eurekaka.bricks.server.model.OrderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    private Info0 info1;
    private Info0 info2;

    // 盘口订单，Market 1与Market 2
    private volatile AsyncStateOrder bidOrder1;
    private volatile AsyncStateOrder askOrder1;

    private volatile AsyncStateOrder bidOrder2;
    private volatile AsyncStateOrder askOrder2;

    private volatile long posQuantity1;
    private volatile long posQuantity2;

    // 上次下单时间
    private volatile long lastOrderTime1;
    private volatile long lastOrderTime2;

    private volatile long timeCounter;

    private int maxPositionQuantity;
    private int minOrderQuantity;

    private double sizePrecision;

    private int orderIndex1;
    private int orderIndex2;
    private final Map<String, Double> filledOrderSize;

    public Strategy07(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.accountActor = new AccountActor(brickContext.getAccountManager());

        this.orderIndex1 = 0;
        this.orderIndex2 = 0;

        filledOrderSize = new ConcurrentHashMap<>();
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

        posQuantity1 = accountActor.getPosition(info1).getQuantity();
        posQuantity2 = accountActor.getPosition(info2).getQuantity();

        timeCounter = System.currentTimeMillis();

        accountActor.cancelAllOrders(info1);
        accountActor.cancelAllOrders(info2);
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
        if (System.currentTimeMillis() - timeCounter > 300000) {
            timeCounter = System.currentTimeMillis();
        }

        // 清理订单通知记录
        filledOrderSize.entrySet().removeIf(e ->
                (bidOrder1 == null || !e.getKey().equals(bidOrder1.getClientOrderId())) &&
                (askOrder1 == null || !e.getKey().equals(askOrder1.getClientOrderId())) &&
                (bidOrder2 == null || !e.getKey().equals(bidOrder2.getClientOrderId())) &&
                (askOrder2 == null || !e.getKey().equals(askOrder2.getClientOrderId())));

        boolean checked1 = checkOrderInterval(1);
        AsyncStateOrder o1 = updateOrder(info1, info2, OrderSide.BUY,
                bidOrder1, posQuantity1, checked1);
        if (o1 != null) {
            bidOrder1 = o1;
        }

        AsyncStateOrder o2 = updateOrder(info1, info2, OrderSide.SELL,
                askOrder1, posQuantity1, checked1);
        if (o2 != null) {
            askOrder1 = o2;
        }

        boolean checked2 = checkOrderInterval(2);
        AsyncStateOrder o3 = updateOrder(info2, info1, OrderSide.BUY,
                bidOrder2, posQuantity2, checked2);
        if (o3 != null) {
            bidOrder2 = o3;
        }

        AsyncStateOrder o4 = updateOrder(info2, info1, OrderSide.SELL,
                askOrder2, posQuantity2, checked2);
        if (o4 != null) {
            askOrder2 = o4;
        }

    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        if (notification instanceof OrderNotification) {
            OrderNotification orderNotify = (OrderNotification) notification;
            if (orderNotify.getFilledSize() > 0) {
                logger.info("{}: received order notify: {}", System.currentTimeMillis() - timeCounter, orderNotify);
                // 确认成交订单来自于当前四个挂单
                AsyncStateOrder order = null;
                if (bidOrder1 != null && orderNotify.getClientOrderId().equals(bidOrder1.getClientOrderId()) ||
                        askOrder1 != null && orderNotify.getClientOrderId().equals(askOrder1.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info2);
                    if (order != null && order.getQuantity() > 0) {
                        long base = posQuantity1;
                        if (orderNotify.getSide().equals(OrderSide.BUY)) {
                            posQuantity1 = base + order.getQuantity();
                        } else {
                            posQuantity1 = base - order.getQuantity();
                        }
                    }
                } else if (bidOrder2 != null && orderNotify.getClientOrderId().equals(bidOrder2.getClientOrderId()) ||
                        askOrder2 != null && orderNotify.getClientOrderId().equals(askOrder2.getClientOrderId())) {
                    order = generateMarketHedgingOrder(orderNotify, info1);
                    if (order != null && order.getQuantity() > 0) {
                        long base = posQuantity2;
                        if (orderNotify.getSide().equals(OrderSide.BUY)) {
                            posQuantity2 = base + order.getQuantity();
                        } else {
                            posQuantity2 = base - order.getQuantity();
                        }
                    }
                }
                if (order != null) {
                    // 直接市价单对冲
                    AsyncStateOrder finalOrder = order;
                    accountActor.asyncMakeOrder(order).thenAccept(o -> {
                        finalOrder.setState(OrderState.SUBMITTED);
                        logger.info("{}: made trade hedging order: {}", System.currentTimeMillis() - timeCounter, o);
                    });
                    // todo:: 支持IOC or GTC 对冲
                }
            }
        }
    }

    private AsyncStateOrder generateMarketHedgingOrder(OrderNotification orderNotify, Info0 other) {
        double sizeDiff = orderNotify.getFilledSize() -
                filledOrderSize.getOrDefault(orderNotify.getClientOrderId(), 0D);
        if (sizeDiff > 0) {
            long quantity = Math.round(sizeDiff * orderNotify.getPrice());
            if (quantity < minOrderQuantity) {
                logger.info("too small order filled size diff: {}, order: {}", sizeDiff, orderNotify);
                return null;
            }
            filledOrderSize.put(orderNotify.getClientOrderId(), orderNotify.getFilledSize());

            // 记录所有成交信息
            OrderSide side = OrderSide.BUY;
            if (orderNotify.getSide().equals(OrderSide.BUY)) {
                // 下卖单对冲
                side = OrderSide.SELL;
            }
            double size = Utils.round(sizeDiff, sizePrecision);

            String clientOrderId = orderNotify.getName() + "_" + System.currentTimeMillis() + orderIndex2++;
            double price = orderNotify.getPrice();

            return new AsyncStateOrder(other.getAccount(), other.getName(), other.getSymbol(),
                    side, OrderType.MARKET, size, price, quantity, clientOrderId, OrderState.SUBMITTING);
        }

        return null;
    }

    private AsyncStateOrder updateOrder(Info0 info, Info0 other, OrderSide side,
                              AsyncStateOrder currentOrder, long posQuantity, boolean checked) throws StrategyException {
        if (currentOrder != null && (currentOrder.getState().equals(OrderState.SUBMITTING) ||
                currentOrder.getState().equals(OrderState.CANCELLING))) {
            return null;
        }

        // 生成推荐订单，order id = null
        AsyncStateOrder order = generateBaseOrder(info, other, side);

        // 始终保证第一时间取消价格错误的订单
        // 异步取消订单，等待下个周期再下单
        if (currentOrder != null && currentOrder.getState().equals(OrderState.SUBMITTED)) {
            // 检查是否需要撤单
            if (side.equals(OrderSide.BUY)) {
                double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
                if (currentOrder.getPrice() > order.getPrice() ||
                        currentOrder.getPrice() < order.getPrice() * (1 - baseOrderCancelRate)) {
                    currentOrder.setState(OrderState.CANCELLING);
                    accountActor.asyncCancelOrder(currentOrder).thenAccept(unused -> {
                        currentOrder.setState(OrderState.CANCELLED);
                        logger.info("{}: cancel bid order: {}",
                                System.currentTimeMillis() - timeCounter, currentOrder);
                    });
                    return null;
                }
            } else {
                double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
                if (currentOrder.getPrice() < order.getPrice() ||
                        currentOrder.getPrice() > order.getPrice() * (1 + baseOrderCancelRate)) {
                    currentOrder.setState(OrderState.CANCELLING);
                    accountActor.asyncCancelOrder(currentOrder).thenAccept(unused -> {
                        currentOrder.setState(OrderState.CANCELLED);
                        logger.info("{}: cancel ask order: {}",
                                System.currentTimeMillis() - timeCounter, currentOrder);
                    });
                    return null;
                }
            }
        }

        // todo:: 检查订单是否满足下单条件，足够余额，价格范围等
        if (side.equals(OrderSide.BUY) && posQuantity + order.getQuantity() >= maxPositionQuantity ||
                side.equals(OrderSide.SELL) && posQuantity - order.getQuantity() <= -maxPositionQuantity) {
            // 超出最大仓位限制，不再下单
            return null;
        }

        if (order.getQuantity() < minOrderQuantity) {
            return null;
        }

        // 若是可以下单
        if (currentOrder == null || currentOrder.getState().equals(OrderState.CANCELLED)) {
            if (checked) {
                String clientOrderId = info.getName() + "_" + System.currentTimeMillis() + "_" + orderIndex1++;
                order.setClientOrderId(clientOrderId);

                accountActor.asyncMakeOrder(order).thenAccept(newOrder -> {
                    if (OrderStatus.NEW.equals(newOrder.getStatus())) {
                        order.setState(OrderState.SUBMITTED);
                    } else {
                        order.setState(OrderState.CANCELLED);
                    }
                    logger.info("{}: made new order: {}", System.currentTimeMillis() - timeCounter, newOrder);
                });

                return order;
            }
        }

        return null;
    }

    private AsyncStateOrder generateBaseOrder(Info0 info, Info0 other, OrderSide side) throws StrategyException {
        if (!side.equals(OrderSide.BUY) && !side.equals(OrderSide.SELL)) {
            throw new StrategyException("side error: " + side);
        }

        int orderQuantity = strategyConfig.getInt("order_quantity", 100);
        if (strategyConfig.getBoolean("rand_order_quantity", true)) {
            orderQuantity += System.nanoTime() % orderQuantity;
        }

        AsyncStateOrder order = null;
        if (side.equals(OrderSide.BUY)) {
            DepthPrice depthPrice = accountActor.getBidDepthPrice(other);
            // 挂单，买一价价差配置
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            double price = depthPrice.price;

            price = price * (1 - bidPriceRate);
            price = price * (1 - accountActor.getTakerRate(other));
            price = price * (1 - accountActor.getMakerRate(info));
            price = Utils.floor(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new AsyncStateOrder(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT_GTX, size, price, orderQuantity, OrderState.SUBMITTING);
        } else {
            DepthPrice depthPrice = accountActor.getAskDepthPrice(other);
            // 挂单，卖一价价差配置
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = depthPrice.price;

            // 允许挂卖单
            price = price * (1 + askPriceRate);
            price = price * (1 + accountActor.getTakerRate(other));
            price = price * (1 + accountActor.getMakerRate(info));
            price = Utils.ceil(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new AsyncStateOrder(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT_GTX, size, price, orderQuantity, OrderState.SUBMITTING);
        }

        return order;
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
     * @param type type == 1，info1；type == 2，info2
     * @return 是否允许下单
     */
    private boolean checkOrderInterval(int type) {
        long currentTime = System.currentTimeMillis();
        int orderInterval = strategyConfig.getInt("order_interval", 1000);

        if (type == 1) {
            if (currentTime - lastOrderTime1 < orderInterval) {
                return false;
            }
            lastOrderTime1 = currentTime;
        } else if (type == 2) {
            if (currentTime - lastOrderTime2 < orderInterval) {
                return false;
            }
            lastOrderTime2 = currentTime;
        }
        return true;
    }
}