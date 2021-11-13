package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.*;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.executor.FutureOrderExecutorV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.eurekaka.bricks.common.model.ReportEvent.EventType.STRATEGY_NOT_BALANCE;
import static org.eurekaka.bricks.common.util.Utils.PRECISION;

/**
 * 做市套利策略，即时对冲
 * 以套利算法为基础，其次目标做市商
 *
 * 1. binance gate 互相挂单合约对冲，简单1v1
 */
public class Strategy03 implements Strategy {
    private final static Logger logger = LoggerFactory.getLogger(Strategy03.class);

    private final StrategyConfig strategyConfig;
    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;

    private final AccountActor accountActor;
    private final OrderExecutor orderExecutor;
    // 只需要紧盯4个盘口订单
    private Order bidOrder1;
    private Order askOrder1;

    private Order bidOrder2;
    private Order askOrder2;

    private Info0 info1;
    private Info0 info2;

    private final Map<String, Double> orderFilledSize;
    private double currentFilledSize;
    private long currentFilledQuantity;

    private long nextBalanceTime;
    private long balanceInterval;

    // state 0: 不可运行状态； 1：正常基础挂单；2：平仓状态；
    private int currentState;

    public Strategy03(StrategyConfig strategyConfig,
                      AccountManager accountManager,
                      InfoState<Info0, ?> infoState,
                      OrderExecutor orderExecutor) {
        this.strategyConfig = strategyConfig;
        this.accountManager = accountManager;
        this.infoState = infoState;
        this.orderExecutor = orderExecutor;

        this.accountActor = new AccountActor(accountManager);

        this.orderFilledSize = new ConcurrentHashMap<>();
        currentState = 0;
    }

    public Strategy03(StrategyConfig strategyConfig,
                      AccountManager accountManager,
                      InfoState<Info0, ?> infoState) {
        this(strategyConfig, accountManager, infoState,
                new FutureOrderExecutorV1(strategyConfig.getProperties(), accountManager, infoState));
    }

    @Override
    public void start() throws StrategyException {
        List<Info0> infos = infoState.getInfoByName(strategyConfig.getInfoName());
        if (infos.size() != 2) {
            throw new StrategyException("it must be only 2 infos with same name: "
                    + strategyConfig.getInfoName());
        }
        info1 = infos.get(0);
        info2 = infos.get(1);

        // 每半小时检查一次仓位平衡
        balanceInterval = strategyConfig.getInt("balance_interval", 60000 * 30);
        nextBalanceTime = System.currentTimeMillis() / balanceInterval * balanceInterval + balanceInterval;

        this.orderExecutor.start();

        // 清理当前订单 open orders
        cleanOrders();

        currentFilledSize = 0;
        currentFilledQuantity = 0;
        currentState = 1;
        logger.info("strategy {} started", strategyConfig.getName());
    }

    @Override
    public void stop() throws StrategyException {
        // 清理所有挂单
        cleanOrders();

        orderExecutor.stop();

        logger.info("strategy {} stopped", strategyConfig.getName());
    }

    /**
     * 1. 检查当前挂单，价格数量
     * 2. 定量根据仓位再平衡（触发max_position_qty)
     *      再平衡订单，以买一卖一价格同时平仓
     */
    @Override
    public void run() throws StrategyException {
        long maxPositionQuantity = strategyConfig.getLong("max_position_qty", 1000);
        long minPositionQuantity = strategyConfig.getLong("min_position_qty", 23);

        PositionValue pos1 = accountActor.getPosition(info1);
        PositionValue pos2 = accountActor.getPosition(info2);

        // 确定多仓与空仓平衡
        if (currentState == 1 &&
                ((pos1.getQuantity() > maxPositionQuantity && pos2.getQuantity() < -maxPositionQuantity) ||
                 (pos1.getQuantity() < -maxPositionQuantity && pos2.getQuantity() > maxPositionQuantity)) &&
                (bidOrder1 == null && askOrder1 == null && bidOrder2 == null && askOrder2 == null) &&
                orderExecutor.notBusy()) {
            // 检查当前挂单是否都已经为空
            // 确定已经完全撤单，多空仓位不会再变化，开始平仓
            // todo:: 等待价格合理反转，买价低于卖价时再平仓
            DepthPrice bidPrice = null;
            DepthPrice askPrice = null;

            if (pos1.getQuantity() > 0 && pos2.getQuantity() < 0) {
                // 获取info1 的买一价格，info2的卖一价格
                askPrice = accountActor.getBidDepthPrice(info1);
                bidPrice = accountActor.getAskDepthPrice(info2);
            } else if (pos1.getQuantity() < 0 && pos2.getQuantity() > 0) {
                askPrice = accountActor.getBidDepthPrice(info2);
                bidPrice = accountActor.getAskDepthPrice(info1);
            }

            double closeProfitRate = strategyConfig.getDouble("close_profit_rate", 0.0003);
            if (bidPrice != null && askPrice != null &&
                    askPrice.price > bidPrice.price * (1 + (closeProfitRate))) {
                // 设置info允许下单方向
                if (pos1.getQuantity() > 0) {
                    info1.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.SELL.name());
                } else {
                    info1.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.BUY.name());
                }
                if (pos2.getQuantity() > 0) {
                    info2.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.SELL.name());
                } else {
                    info2.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.BUY.name());
                }

                logger.info("making position close orders: \npos1: {}, \npos2: {}", pos1, pos2);
                orderExecutor.makeOrder(pos1.getName(), -pos1.getQuantity(),
                        Math.round(pos1.getPrice() * PRECISION));
                orderExecutor.makeOrder(pos2.getName(), -pos2.getQuantity(),
                        Math.round(pos2.getPrice() * PRECISION));

                currentState = 2;
            }
        } else if (currentState == 2 && Math.abs(pos1.getQuantity()) < minPositionQuantity &&
                Math.abs(pos2.getQuantity()) < minPositionQuantity && orderExecutor.notBusy()) {
            // 切换回建仓状态
            currentState = 1;
            info1.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.ALL.name());
            info2.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.ALL.name());
        } else if (currentState == 1) {
            // 检查现有挂单的价格
            // info1 买单价格，依照info2 买单方向有效深度价格确定
            currentFilledSize = 0;
            currentFilledQuantity = 0;

            bidOrder1 = updateOrder(bidOrder1, OrderSide.BUY, info1, info2, pos1.getQuantity());
            askOrder1 = updateOrder(askOrder1, OrderSide.SELL, info1, info2, pos1.getQuantity());

            bidOrder2 = updateOrder(bidOrder2, OrderSide.BUY, info2, info1, pos2.getQuantity());
            askOrder2 = updateOrder(askOrder2, OrderSide.SELL, info2, info1, pos2.getQuantity());

            // 根据收集的filled size，quantity对冲
            if (currentFilledSize != 0) {
                long price = Math.round(currentFilledQuantity * 1.0 * PRECISION / currentFilledSize);
                orderExecutor.makeOrder(strategyConfig.getInfoName(), -currentFilledQuantity, price);
            }
        }

        // 间隔一定时间检查仓位是否平衡
        long currentTime = System.currentTimeMillis();
        if (currentTime > nextBalanceTime) {
            long diffPos = pos1.getQuantity() + pos2.getQuantity();
            if (Math.abs(diffPos) > minPositionQuantity * 10) {
                String content = "unbalance positions: \npos1: " +
                        pos1.getQuantity() + "\npos2: " + pos2.getQuantity();
                logger.warn(content);
                MonitorReporter.report(STRATEGY_NOT_BALANCE + strategyConfig.getName(),
                        new ReportEvent(STRATEGY_NOT_BALANCE, ReportEvent.EventLevel.WARNING, content));
            }

            nextBalanceTime = currentTime / balanceInterval * balanceInterval + balanceInterval;
        }

        // 清理不必要的订单成交数量记录
        orderFilledSize.entrySet().removeIf(e ->
                !((bidOrder1 != null && e.getKey().equals(bidOrder1.getOrderId())) ||
                (askOrder1 != null && e.getKey().equals(askOrder1.getOrderId())) ||
                (bidOrder2 != null && e.getKey().equals(bidOrder2.getOrderId())) ||
                (askOrder2 != null && e.getKey().equals(askOrder2.getOrderId()))));
    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        // 收到trade notification，检查是否为自身挂单的订单成交

        // 做一个小订单缓存，处理记录非常快，
        if (notification instanceof TradeNotification) {
            TradeNotification trade = (TradeNotification) notification;
            // 确定订单来自做市单（对冲单成交不对冲）
            if (checkTrade(trade)) {
                logger.info("receive trade: {}", trade);
//                long quantity = Math.round(trade.getResult());
                // 买单被成交，对冲一个卖单
//                if (trade.getSide().equals(OrderSide.BUY)) {
//                    quantity = -quantity;
//                }
//                orderExecutor.makeOrder(trade.getName(), quantity, Math.round(trade.getPrice() * PRECISION));
            }
        } else if (notification instanceof OrderNotification) {
            orderExecutor.notify((OrderNotification) notification);
        }
    }

    private boolean checkTrade(TradeNotification trade) {
        Order currentOrder = null;
        if (bidOrder1 != null && trade.getOrderId().equals(bidOrder1.getOrderId())) {
            currentOrder = bidOrder1;
        } else if (askOrder1 != null && trade.getOrderId().equals(askOrder1.getOrderId())) {
            currentOrder = askOrder1;
        } else if (bidOrder2 != null && trade.getOrderId().equals(bidOrder2.getOrderId())) {
            currentOrder = bidOrder2;
        } else if (askOrder2 != null && trade.getOrderId().equals(askOrder2.getOrderId())) {
            currentOrder = askOrder2;
        }

        if (currentOrder != null) {
            double filledSize = orderFilledSize.getOrDefault(currentOrder.getOrderId(), 0D);
            filledSize += trade.getSize();
            orderFilledSize.put(currentOrder.getOrderId(), filledSize);
            return true;
        }

        return false;
    }

    private void cleanOrders() throws StrategyException {
        if (!strategyConfig.getBoolean("is_debug", false)) {
            accountActor.cancelAllOrders(info1);
            accountActor.cancelAllOrders(info2);
        }

        // 初始挂单
        bidOrder1 = null;
        askOrder1 = null;
        bidOrder2 = null;
        askOrder2 = null;
    }

    private Order updateOrder(Order currentOrder, OrderSide side,
                              Info0 current, Info0 other, long positionQuantity) {
        // 根据行情生成新的订单
        Order updatedOrder = generateOrder(side, current, other, positionQuantity);
        if (updatedOrder == null) {
            // 撤销原有订单
            if (currentOrder != null && currentOrder.getOrderId() != null) {
                if (cancelOrder(current, currentOrder.getOrderId())) {
                    return null;
                }
            }
        } else if (currentOrder == null || currentOrder.getOrderId() == null) {
            makeOrder(updatedOrder);
            return updatedOrder;
        } else {
            double priceRate = strategyConfig.getDouble("price_rate", 0.003);
            long minPositionQuantity = strategyConfig.getInt("min_position_qty", 11);
            // 原有订单
            // 挂单的金额已经很小时，取消订单，重新下单
            double leftSize = currentOrder.getSize() -
                    orderFilledSize.getOrDefault(currentOrder.getOrderId(), 0D);
            long leftQuantity = Math.round(leftSize * currentOrder.getPrice());

            if (side.equals(OrderSide.BUY)) {
                double diff = (updatedOrder.getPrice() - currentOrder.getPrice()) / updatedOrder.getPrice();
                if (diff < 0 ||  diff > priceRate || leftQuantity < minPositionQuantity) {
                    // 撤销
                    if (cancelOrder(current, currentOrder.getOrderId())) {
                        makeOrder(updatedOrder);
                        return updatedOrder;
                    }
                }
            } else {
                // 卖单
                double diff = (currentOrder.getPrice() - updatedOrder.getPrice()) / updatedOrder.getPrice();
                if (diff < 0 || diff > priceRate || leftQuantity < minPositionQuantity) {
                    // 撤销
                    if (cancelOrder(current, currentOrder.getOrderId())) {
                        makeOrder(updatedOrder);
                        return updatedOrder;
                    }
                }
            }
        }
        // 保持不变
        return currentOrder;
    }

    private double getGoodPrice(OrderSide side, Info0 current, Info0 other) {
        try {
            double minPriceRate = strategyConfig.getDouble("profit_rate", 0.001);
            double currentFundingRate = accountActor.getFundingRate(current);
            double otherFundingRate = accountActor.getFundingRate(other);
            Exchange currentAccount = accountManager.getAccount(current.getAccount());
            double currentFeeRate = currentAccount.getTakerRate() + currentAccount.getMakerRate();
            Exchange otherAccount = accountManager.getAccount(other.getAccount());
            double otherFeeRate = otherAccount.getTakerRate() + otherAccount.getMakerRate();
            if (side.equals(OrderSide.BUY)) {
                DepthPrice depthPrice = accountActor.getBidDepthPrice(other);
                double goodPrice = depthPrice.price / (1 + currentFeeRate);
                goodPrice /= (1 + otherFeeRate);
                goodPrice /= (1 + minPriceRate);
                goodPrice /= (1 + currentFundingRate);
                goodPrice /= (1 + otherFundingRate);
//            logger.debug("buy good price: {}, depth price: {}, current fee rate: {}, other fee rate: {}, " +
//                    "profit rate: {}, current funding rate: {}, other funding rate: {}",
//                    goodPrice, depthPrice.price, currentFeeRate, otherFeeRate,
//                    minPriceRate, currentFundingRate, otherFundingRate);
                // 价格精度处理
                return Math.floor(goodPrice * current.getPricePrecision()) / current.getPricePrecision();
            } else if (side.equals(OrderSide.SELL)) {
                DepthPrice depthPrice = accountActor.getAskDepthPrice(other);
                double goodPrice = depthPrice.price * (1 + currentFeeRate);
                goodPrice *= (1 + otherFeeRate);
                goodPrice *= (1 + minPriceRate);
                goodPrice /= (1 + currentFundingRate);
                goodPrice /= (1 + otherFundingRate);
//            logger.debug("sell good price: {}, depth price: {}, current fee rate: {}, other fee rate: {}, " +
//                            "profit rate: {}, current funding rate: {}, other funding rate: {}",
//                    goodPrice, depthPrice.price, currentFeeRate, otherFeeRate,
//                    minPriceRate, currentFundingRate, otherFundingRate);
                // 价格精度处理
                return Math.ceil(goodPrice * current.getPricePrecision()) / current.getPricePrecision();
            }
        } catch (Exception e) {
            logger.error("failed to calculate good price", e);
        }
        return 0;
    }

    private Order generateOrder(OrderSide side, Info0 current, Info0 other,
                                long positionQuantity) {
        // 超过持仓上限时，直接返回空订单，保持程序进入清仓状态
        long maxPositionQuantity = strategyConfig.getLong("max_position_qty", 1000);
        if (Math.abs(positionQuantity) > maxPositionQuantity) {
            return null;
        }

        // 随机订单金额
        long orderQuantity = strategyConfig.getLong("order_quantity", 100);
        if (!strategyConfig.getBoolean("is_debug", false)) {
            orderQuantity += System.currentTimeMillis() % orderQuantity;
        }
        // 根据仓位调整订单金额
        if (side.equals(OrderSide.BUY) && positionQuantity + orderQuantity < 0) {
             // 生成买单，但仓位为空
            orderQuantity = -positionQuantity;
        } else if (side.equals(OrderSide.SELL) && positionQuantity - orderQuantity > 0) {
            orderQuantity = positionQuantity;
        }

        double goodPrice = getGoodPrice(side, current, other);
        if (goodPrice == 0) {
            // 未获得有效价格
            return null;
        }

        double size = Utils.round(orderQuantity / goodPrice, current.getSizePrecision());
        return new Order(current.getAccount(), current.getName(), current.getSymbol(),
                side, OrderType.LIMIT, size, goodPrice, Math.round(orderQuantity));
    }

    private void makeOrder(Order order) {
        String orderId = null;
        if (strategyConfig.getBoolean("is_test", false)) {
            orderId = "id-" + System.nanoTime();
            order.setOrderId(orderId);
            logger.info("make order: {}", order);
        } else {
            try {
                orderId = accountActor.makeOrder(order);
                if (orderId.startsWith("FAIL_OK")) {
                    orderId = null;
                }
                order.setOrderId(orderId);
            } catch (StrategyException e) {
                logger.error("failed to make order: {}", order, e);
            }
        }
    }

    private boolean cancelOrder(Info0 info, String orderId) {
        if (strategyConfig.getBoolean("is_test", false)) {
            logger.info("cancel order id: {}", orderId);
            return true;
        }
        try {
            CurrentOrder currentOrder = accountActor.cancelOrder(info, orderId);
            if (currentOrder != null) {
                if (currentOrder.getFilledSize() > 0.00000001) {
                    logger.info("cancelled order: {}", currentOrder);
                    if (currentOrder.getSide().equals(OrderSide.BUY)) {
                        currentFilledSize += currentOrder.getFilledSize();
                        currentFilledQuantity += Math.round(currentOrder.getFilledSize() * currentOrder.getPrice());
                    } else {
                        currentFilledSize -= currentOrder.getFilledSize();
                        currentFilledQuantity -= Math.round(currentOrder.getFilledSize() * currentOrder.getPrice());
                    }
                }
                return true;
            }
        } catch (StrategyException e) {
            logger.error("failed to cancel Order, id: {}", orderId, e);
        }
        return false;
    }
}
