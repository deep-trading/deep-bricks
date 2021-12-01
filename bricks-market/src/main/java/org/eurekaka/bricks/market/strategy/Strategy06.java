package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 双向持仓挂单对冲成交，基于Strategy05优化
 * 双向挂单能够有效促进成交，提高开仓，平仓率
 *
 * 1. 定期检查仓位对冲
 * 2. 根据成交订单对冲
 * 4. 设置最大仓位，订单平均仓位，定时检查仓位风险
 */
public class Strategy06 implements Strategy {
    private final Logger logger = LoggerFactory.getLogger(Strategy06.class);

    private final BrickContext brickContext;
    private final StrategyConfig strategyConfig;

    private final AccountActor accountActor;

    private Info0 info1;
    private Info0 info2;

    // 盘口订单，Market 1与Market 2
    private volatile Order bidOrder1;
    private volatile Order askOrder1;

    private volatile Order bidOrder2;
    private volatile Order askOrder2;

    private volatile long posQuantity1;
    private volatile long posQuantity2;

    // 上次下单时间
    private volatile long lastOrderTime1;
    private volatile long lastOrderTime2;
    private volatile long lastPositionTime;

    private final Object lock;
    private Executor executor;
    private long baseTimer;

    private int maxPositionQuantity;
    private int minOrderQuantity;

    private double sizePrecision;

    public Strategy06(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.accountActor = new AccountActor(brickContext.getAccountManager());

        // 跟踪执行订单操作，挂单与撤单
        lock = new Object();
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

        executor = Executors.newFixedThreadPool(strategyConfig.getInt("executor_num", 6));
        baseTimer = System.currentTimeMillis();

        // 清理残留订单？？？
        accountActor.cancelAllOrders(info1);
        accountActor.cancelAllOrders(info2);
    }

    @Override
    public void stop() throws StrategyException {
        cancelOrder(info1, bidOrder1);
        cancelOrder(info1, askOrder1);

        cancelOrder(info2, bidOrder2);
        cancelOrder(info2, askOrder2);
    }

    /**
     * 定时检查挂单价格是否正确
     * 定时查询仓位是否平衡
     * @throws StrategyException
     */
    @Override
    public void run() throws StrategyException {
        if (!brickContext.getAccount(info1.getAccount()).isAlive() ||
                !brickContext.getAccount(info2.getAccount()).isAlive()) {
            logger.warn("account dead, waiting...");
            try {
                cancelAllOrders().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.info("failed to cancel all orders", e);
            }
            return;
        }

        // 仓位平衡，更新挂单
        boolean checked1 = checkOrderInterval(1);
        boolean checked2 = checkOrderInterval(2);
        boolean checked3 = checkPositionInterval();

        if (checked3) {
            // 撤销所有订单，再检查仓位
            try {
                cancelAllOrders().thenRun(this::balancePosition).get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("failed to balance positions", e);
            }
        } else {
            try {
                CompletableFuture.allOf(CompletableFuture.supplyAsync(() ->
                                updateOrder(info1, info2, OrderSide.BUY, bidOrder1, posQuantity1))
                        .thenAcceptAsync(order -> {
                            if (order != null && checked1) {
                                makeOrder(order);
                                if (order.getOrderId() != null) {
                                    bidOrder1 = order;
                                }
                            }
                        }, executor), CompletableFuture.supplyAsync(() ->
                                updateOrder(info1, info2, OrderSide.SELL, askOrder1, posQuantity1))
                        .thenAcceptAsync(order -> {
                            if (order != null && checked1) {
                                makeOrder(order);
                                if (order.getOrderId() != null) {
                                    askOrder1 = order;
                                }
                            }
                        }, executor), CompletableFuture.supplyAsync(() ->
                                updateOrder(info2, info1, OrderSide.BUY, bidOrder2, posQuantity2))
                        .thenAcceptAsync(order -> {
                            if (order != null && checked2) {
                                makeOrder(order);
                                if (order.getOrderId() != null) {
                                    bidOrder2 = order;
                                }
                            }
                        }, executor), CompletableFuture.supplyAsync(() ->
                                updateOrder(info2, info1, OrderSide.SELL, askOrder2, posQuantity2))
                        .thenAcceptAsync(order -> {
                            if (order != null && checked2) {
                                makeOrder(order);
                                if (order.getOrderId() != null) {
                                    askOrder2 = order;
                                }
                            }
                        }, executor)).get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("update orders failed", e);
            }
        }
    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        if (notification instanceof TradeNotification) {
            TradeNotification trade = (TradeNotification) notification;
            logger.info("{}: received trade: {}", System.currentTimeMillis() - baseTimer, trade);
            // 确认成交订单来自于当前四个挂单
            Order order = null;
            if (bidOrder1 != null && trade.getOrderId().equals(bidOrder1.getOrderId()) ||
                    askOrder1 != null && trade.getOrderId().equals(askOrder1.getOrderId())) {
                order = generateTradeHedgingOrder(trade, info2);
                long base = posQuantity1;
                if (trade.getSide().equals(OrderSide.BUY)) {
                    posQuantity1 = base + Math.round(trade.getResult());
                } else {
                    posQuantity1 = base - Math.round(trade.getResult());
                }
            } else if (bidOrder2 != null && trade.getOrderId().equals(bidOrder2.getOrderId()) ||
                    askOrder2 != null && trade.getOrderId().equals(askOrder2.getOrderId())) {
                order = generateTradeHedgingOrder(trade, info1);
                long base = posQuantity2;
                if (trade.getSide().equals(OrderSide.BUY)) {
                    posQuantity2 = base + Math.round(trade.getResult());
                } else {
                    posQuantity2 = base - Math.round(trade.getResult());
                }
            }
            if (order != null) {
                Order finalOrder = order;
                CompletableFuture.runAsync(() -> {
                    logger.info("{}: trying to make trade hedging order: {}",
                            System.currentTimeMillis() - baseTimer, finalOrder);
                    makeOrder(finalOrder);
                    if (finalOrder.getOrderId() == null) {
                        logger.error("{}: failed to make trade hedging order, order id is null",
                                System.currentTimeMillis() - baseTimer);
                    }
                    logger.info("{}: made trade hedging order: {}", System.currentTimeMillis() - baseTimer, finalOrder);
                }, executor);
            }
        }
    }

    /**
     * 撤销订单超时，直接返回等待下次再取消订单
     * 下单超时，直接获取全部订单，再取消
     * @return
     */
    private Order updateOrder(Info0 info, Info0 other, OrderSide side,
                              Order currentOrder, long posQuantity) {
        try {
            Order order = generateBaseOrder(info, other, side);
            if (order != null) {
                // 检查当前挂单，确认是否需要撤单
                if (currentOrder != null && currentOrder.getOrderId() != null) {
                    if (side.equals(OrderSide.BUY)) {
                        double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
                        if (currentOrder.getPrice() > order.getPrice() ||
                                currentOrder.getPrice() < order.getPrice() * (1 - baseOrderCancelRate)) {
                            CurrentOrder cancelOrder = accountActor.cancelOrder(info, currentOrder.getOrderId());
                            logger.debug("{}: cancel bid order: {}", System.currentTimeMillis() - baseTimer, cancelOrder);
                            currentOrder.setOrderId(null);
                        } else {
                            return null;
                        }
                    } else {
                        double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
                        if (currentOrder.getPrice() < order.getPrice() ||
                                currentOrder.getPrice() > order.getPrice() * (1 + baseOrderCancelRate)) {
                            CurrentOrder cancelOrder = accountActor.cancelOrder(info, currentOrder.getOrderId());
                            logger.debug("{}: cancel ask order: {}", System.currentTimeMillis() - baseTimer, cancelOrder);
                            currentOrder.setOrderId(null);
                        } else {
                            return null;
                        }
                    }
                }

                if (side.equals(OrderSide.BUY) && posQuantity + order.getQuantity() >= maxPositionQuantity ||
                        side.equals(OrderSide.SELL) && posQuantity - order.getQuantity() <= -maxPositionQuantity) {
                    // 超出最大仓位限制，不再下单
                    return null;
                }

                return order;
            }
        } catch (StrategyException e) {
            logger.error("failed to update current order: {}", currentOrder, e);
        }

        return null;
    }

    /**
     * 生成基础挂单
     * @param info 目标挂单的交易对信息
     * @param other 参考交易对信息
     * @param side 挂单方向
     * @return 可以下单的订单
     * @throws StrategyException 生成失败
     */
    private Order generateBaseOrder(Info0 info, Info0 other, OrderSide side) throws StrategyException {
        if (!side.equals(OrderSide.BUY) && !side.equals(OrderSide.SELL)) {
            throw new StrategyException("side error: " + side);
        }

        int orderQuantity = strategyConfig.getInt("order_quantity", 100);
        if (strategyConfig.getBoolean("rand_order_quantity", true)) {
            orderQuantity += System.nanoTime() % orderQuantity;
        }

        Order order = null;
        if (side.equals(OrderSide.BUY)) {
            DepthPrice depthPrice = accountActor.getBidDepthPrice(other);
            // 挂单，买一价价差配置
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            double price = depthPrice.price;

            price = price * (1 - bidPriceRate);
            price = price * (1 - accountActor.getTakerRate(other.getAccount()));
            price = price * (1 - accountActor.getMakerRate(info.getAccount()));
            price = Utils.floor(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new Order(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT, size, price, orderQuantity);
        } else {
            DepthPrice depthPrice = accountActor.getAskDepthPrice(other);
            // 挂单，卖一价价差配置
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = depthPrice.price;

            // 允许挂卖单
            price = price * (1 + askPriceRate);
            price = price * (1 + accountActor.getTakerRate(other.getAccount()));
            price = price * (1 + accountActor.getMakerRate(info.getAccount()));
            price = Utils.ceil(price, info.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sizePrecision);

            order = new Order(info.getAccount(), info.getName(), info.getSymbol(),
                    side, OrderType.LIMIT, size, price, orderQuantity);
        }

        // todo:: 检查订单是否满足下单条件，足够余额，价格范围等
        if (order.getQuantity() < strategyConfig.getLong("min_order_quantity", 17L)) {
            return null;
        }

        return order;
    }

    private void balancePosition() {
        try {
            PositionValue pos1 = accountActor.getPosition(info1);
            PositionValue pos2 = accountActor.getPosition(info2);

            posQuantity1 = pos1.getQuantity();
            posQuantity2 = pos2.getQuantity();

            // 仓位不平衡，优先对冲风险头寸，此时可买入或者卖出
            // 所以根据选择，优先削减风险头寸???
            // todo:: 生成对冲订单，比较价格下单对冲（有可能原先交易对账号价格更合适？？）
            long quantity = pos1.getQuantity() + pos2.getQuantity();
            double size = pos1.getSize() + pos2.getSize();

            logger.info("position unbalance, pos1 size: {}, pos2 size: {}", pos1.getSize(), pos2.getSize());

            if (Math.abs(quantity) > minOrderQuantity) {
                makePositionBalanceOrder(-size, -quantity);
            }
        } catch (StrategyException e) {
            logger.error("failed to balance position", e);
        }
    }

    private Order generateTradeHedgingOrder(TradeNotification trade, Info0 other) {
        // 生成对冲订单
        double size = trade.getSize();
        // 记录所有成交信息
        long quantity = Math.round(trade.getResult());
        if (quantity < minOrderQuantity) {
            logger.info("too small trade: {}", trade);
            return null;
        }
        OrderSide side = OrderSide.BUY;
        if (trade.getSide().equals(OrderSide.BUY)) {
            // 下卖单对冲
            side = OrderSide.SELL;
        }
        size = Utils.round(size, sizePrecision);

        return new Order(other.getAccount(), other.getName(), other.getSymbol(),
                side, OrderType.MARKET, size, 0, quantity);
    }



    /**
     * 比较两个交易对，选择价格更好的下单
     * @param size 下单数量，正数代表买单，负数代表卖单
     * @param quantity 下单金额数量
     * @throws StrategyException 下单失败
     */
    private void makePositionBalanceOrder(double size, long quantity) throws StrategyException {
        if (size < 0 && quantity > 0 || size > 0 && quantity < 0) {
            throw new StrategyException("size and quantity is opposite");
        }

        OrderSide side = OrderSide.BUY;
        if (size < 0) {
            side = OrderSide.SELL;
            size = -size;
            quantity = -quantity;
        }

        Order order = null;
        // 比较info1与info2生成的价格
        if (OrderSide.BUY.equals(side)) {
            // 下买单，则比较哪个卖单价格更便宜
            DepthPrice depthPrice1 = accountActor.getAskDepthPrice(info1);
            double price1 = depthPrice1.price * (1 + accountActor.getTakerRate(info1.getAccount()));

            DepthPrice depthPrice2 = accountActor.getAskDepthPrice(info2);
            double price2 = depthPrice2.price * (1 + accountActor.getTakerRate(info2.getAccount()));

            // 此处不需要ceil
            if (price1 < price2) {
                size = Utils.round(size, sizePrecision);
                order = new Order(info1.getAccount(), info1.getName(), info1.getSymbol(),
                        side, OrderType.MARKET, size, 0, quantity);
            } else {
                size = Utils.round(size, sizePrecision);
                order = new Order(info2.getAccount(), info2.getName(), info2.getSymbol(),
                        side, OrderType.MARKET, size, 0, quantity);
            }
        } else {
            // 生成卖单，找最高买价
            DepthPrice depthPrice1 = accountActor.getBidDepthPrice(info1);
            double price1 = depthPrice1.price * (1 - accountActor.getTakerRate(info1.getAccount()));

            DepthPrice depthPrice2 = accountActor.getBidDepthPrice(info2);
            double price2 = depthPrice2.price * (1 - accountActor.getTakerRate(info2.getAccount()));

            if (price1 > price2) {
                size = Utils.round(size, sizePrecision);
                order = new Order(info1.getAccount(), info1.getName(), info1.getSymbol(),
                        side, OrderType.MARKET, size, 0, quantity);
            } else {
                size = Utils.round(size, sizePrecision);
                order = new Order(info2.getAccount(), info2.getName(), info2.getSymbol(),
                        side, OrderType.MARKET, size, 0, quantity);
            }
        }
        logger.info("{}: trying to make position balance order: {}", System.currentTimeMillis() - baseTimer, order);
        makeOrder(order);
        if (order.getOrderId() == null) {
            throw new StrategyException("failed to make position balance order, order id is null");
        }
        logger.info("{}: made position balance order: {}", System.currentTimeMillis() - baseTimer, order);
    }



    private void makeOrder(Order order) {
        try {
            String orderId = accountActor.makeOrder(order);
            if (orderId.startsWith("FAIL_OK")) {
                orderId = null;
            }
            order.setOrderId(orderId);
        } catch (Exception e) {
            logger.error("failed to make order", e);
        }
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

    private boolean checkPositionInterval() {
        long currentTime = System.currentTimeMillis();
        int positionInterval = strategyConfig.getInt("position_interval", 60000);

        if (currentTime - lastPositionTime < positionInterval) {
            return false;
        }
        baseTimer = System.currentTimeMillis();
        lastPositionTime = currentTime;
        return true;
    }

    private void cancelOrder(Info0 info, Order order) {
        if (order != null && order.getOrderId() != null) {
            try {
                accountActor.cancelOrder(info, order.getOrderId());
                order.setOrderId(null);
            } catch (StrategyException e) {
                logger.error("failed to cancel order: {}, id: {}", info.getName(), order.getOrderId());
            }
        }
    }

    private CompletableFuture<Void> cancelAllOrders() {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        if (bidOrder1 != null && bidOrder1.getOrderId() != null) {
            futures.add(CompletableFuture.runAsync(() -> cancelOrder(info1, bidOrder1), executor));
        }
        if (askOrder1 != null && askOrder1.getOrderId() != null) {
            futures.add(CompletableFuture.runAsync(() -> cancelOrder(info1, askOrder1), executor));
        }
        if (bidOrder2 != null && bidOrder2.getOrderId() != null) {
            futures.add(CompletableFuture.runAsync(() -> cancelOrder(info2, bidOrder2), executor));
        }
        if (askOrder2 != null && askOrder2.getOrderId() != null) {
            futures.add(CompletableFuture.runAsync(() -> cancelOrder(info2, askOrder2), executor));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
    }
}
