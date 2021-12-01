package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * 套取手续费抵扣券，只针对套取binance账户优惠券
 * 快速市价单对冲，X交易所挂单，binance市价对冲，X交易所反向挂单，binance再市价反向对冲
 * interval = 100 ms，检查仓位
 *
 * gate合约挂单，binance合约对冲
 *
 * 对冲挂单时，订单成交，但仓位没有变化，怎么办？
 * 速度1s周期，仓位变化肯定推送？？？，500ms，100ms呢？触发下单频率限制？？？订单事件推送时即刻对冲处理？
 *
 */
public class Strategy05 implements Strategy {
    private final Logger logger = LoggerFactory.getLogger(Strategy05.class);

    private final BrickContext brickContext;
    private final StrategyConfig strategyConfig;


    private final AccountActor accountActor;
    // 挂单
    private Order currentBidOrder;
    private Order currentAskOrder;
    // current order info, type = 1
    private Info0 sourceInfo;
    // binance account info, type = 2
    private Info0 targetInfo;

    private long minOrderQuantity;

    private volatile double targetPosSize;
    private final Object lock;

    private long lastOrderTime;

    public Strategy05(BrickContext brickContext,
                      StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.accountActor = new AccountActor(brickContext.getAccountManager());

        this.lock = new Object();
    }

    @Override
    public void start() throws StrategyException {
        // 检查获取两个info配置
        List<Info0> infos = brickContext.getInfoByName(strategyConfig.getInfoName());
        if (infos.size() != 2) {
            throw new StrategyException("it requires two infos: " + infos);
        }
        for (Info0 info : infos) {
            if (info.getType() == 1) {
                sourceInfo = info;
            } else if (info.getType() == 2) {
                targetInfo = info;
            } else {
                throw new StrategyException("error info type: " + info);
            }
        }

        minOrderQuantity = strategyConfig.getLong("min_order_quantity", 13L);

        // initial current position value
        targetPosSize = accountActor.getPosition(targetInfo).getSize();
    }

    @Override
    public void stop() throws StrategyException {
        if (currentBidOrder != null && currentBidOrder.getOrderId() != null) {
            accountActor.cancelOrder(sourceInfo, currentBidOrder.getOrderId());
        }
        if (currentAskOrder != null && currentAskOrder.getOrderId() != null) {
            accountActor.cancelOrder(sourceInfo, currentAskOrder.getOrderId());
        }
    }

    @Override
    public void run() throws StrategyException {
        if (!brickContext.getAccount(sourceInfo.getAccount()).isAlive() ||
                !brickContext.getAccount(targetInfo.getAccount()).isAlive()) {
            logger.warn("account dead, waiting...");
            return;
        }

        synchronized (lock) {
            //检查仓位情况，是否平衡
            PositionValue sourcePosition = accountActor.getPosition(sourceInfo);
            PositionValue targetPosition = accountActor.getPosition(targetInfo);

            if (Math.abs(targetPosSize - targetPosition.getSize()) > 0.0001) {
                // target pos is not satisfied, return
                logger.warn("position: {}, target pos: {}, current pos: {}",
                        targetPosition.getName(), targetPosSize, targetPosition.getSize());
                return;
            }

            if (Math.abs(sourcePosition.getQuantity()) < minOrderQuantity &&
                    Math.abs(targetPosition.getQuantity()) < minOrderQuantity) {
                boolean checked = checkOrderInterval();

                // 平衡，需要双向挂单
                Order o1 = generateBaseOrder(OrderSide.BUY, 0);
                if (o1 != null && checked) {
                    makeOrder(o1);
                    if (o1.getOrderId() != null) {
                        currentBidOrder = o1;
                    }
                }

                Order o2 = generateBaseOrder(OrderSide.SELL, 0);
                if (o2 != null && checked) {
                    makeOrder(o2);
                    if (o2.getOrderId() != null) {
                        currentAskOrder = o2;
                    }
                }
            } else if (Math.abs(sourcePosition.getQuantity()) > minOrderQuantity &&
                    Math.abs(targetPosition.getQuantity()) < minOrderQuantity) {
                // 建立对冲仓位，后重新挂单，加快速度
                updateHedgingPosition(sourcePosition);
                // 期望的对冲仓位
                targetPosSize = -sourcePosition.getSize();
                // 清理挂单
                cleanCurrentOrders();
            } else if (Math.abs(sourcePosition.getQuantity()) > minOrderQuantity &&
                    Math.abs(targetPosition.getQuantity()) > minOrderQuantity) {
                if (Math.abs(targetPosition.getQuantity() + sourcePosition.getQuantity()) < minOrderQuantity) {
                    // 平衡，只需要单向挂单
                    boolean checked = checkOrderInterval();

                    // 仓位平衡
                    if (sourcePosition.getQuantity() > minOrderQuantity) {
                        // market pos 多仓
                        // 此时多仓，只挂卖单平仓
                        Order order = generateBaseOrder(OrderSide.SELL, sourcePosition.getQuantity());
                        if (order != null && checked) {
                            makeOrder(order);
                            if (order.getOrderId() != null) {
                                currentAskOrder = order;
                            }
                        }
                    } else {
                        // sourcePosition.getQuantity() < -minOrderQuantity
                        // market 空仓
                        // 此时空仓，只挂买单平仓
                        Order order = generateBaseOrder(OrderSide.BUY, -sourcePosition.getQuantity());
                        if (order != null && checked) {
                            makeOrder(order);
                            if (order.getOrderId() != null) {
                                currentBidOrder = order;
                            }
                        }
                    }

                } else {
                    // 均有仓位，但不平衡
                    targetPosition.merge(sourcePosition);
                    updateHedgingPosition(targetPosition);
                    // 标记对冲仓位建立
                    targetPosSize = -sourcePosition.getSize();
                }
            } else if (Math.abs(sourcePosition.getQuantity()) < minOrderQuantity &&
                    Math.abs(targetPosition.getQuantity()) > minOrderQuantity) {
                // 此时对冲仓位需要平仓
                updateHedgingPosition(targetPosition);
                targetPosSize = 0;
                cleanCurrentOrders();
            }
        }
    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        // 接收trade notification，加快对冲速度
        if (notification instanceof TradeNotification) {
            TradeNotification trade = (TradeNotification) notification;
            if (trade.getAccount().equals(sourceInfo.getAccount())) {
                synchronized (lock) {
                    if (trade.getSide().equals(OrderSide.BUY) &&
                            currentBidOrder != null && trade.getOrderId().equals(currentBidOrder.getOrderId())) {
                        // 是属于当前挂单的成交，且尚未由仓位检查对冲
                        makeHedgingOrder(OrderSide.SELL, trade.getSize(), Math.round(trade.getResult()));
                        targetPosSize -= trade.getSize();
                        targetPosSize = Utils.round(targetPosSize, targetInfo.getSizePrecision());
                    } else if (trade.getSide().equals(OrderSide.SELL) &&
                            currentAskOrder != null && trade.getOrderId().equals(currentAskOrder.getOrderId())) {
                        makeHedgingOrder(OrderSide.BUY, trade.getSize(), Math.round(trade.getResult()));
                        targetPosSize += trade.getSize();
                        targetPosSize = Utils.round(targetPosSize, targetInfo.getSizePrecision());
                    }
                }
            }
        }
    }

    /**
     * 撤销所有挂单
     * @throws StrategyException
     */
    private void cleanCurrentOrders() throws StrategyException {
        // 取消当前挂单
        if (currentBidOrder != null && currentBidOrder.getOrderId() != null) {
            CurrentOrder currentOrder = accountActor.cancelOrder(sourceInfo, currentBidOrder.getOrderId());
//            logger.info("cancelled current bid order: {}", currentOrder);
            currentBidOrder = null;
        }
        if (currentAskOrder != null && currentAskOrder.getOrderId() != null) {
            CurrentOrder currentOrder = accountActor.cancelOrder(sourceInfo, currentAskOrder.getOrderId());
//            logger.info("cancelled current ask order: {}", currentOrder);
            currentAskOrder = null;
        }
    }

    /**
     * 对冲账户平仓
     * 根据对冲账户平仓
     * 虽然下单后，我们认为账户仓位已经平仓，但可能推送延迟或者不推送仓位信息的异常，
     * 例如下单时恰好断开websocket连接，导致仓位信息更新变慢
     *
     * 对冲账户建仓
     * 根据做市账户仓位建仓
     *
     * @param positionValue 目标账户
     * @throws StrategyException
     */
    private void updateHedgingPosition(PositionValue positionValue) throws StrategyException {
        long quantity = 0;
        double size = 0;
        OrderSide side;
        if (positionValue.getQuantity() > 0) {
            side = OrderSide.SELL;
            size = positionValue.getSize();
            quantity = positionValue.getQuantity();
        } else {
            side = OrderSide.BUY;
            size = -positionValue.getSize();
            quantity = -positionValue.getQuantity();
        }
        makeHedgingOrder(side, size, quantity);
    }

    private void makeHedgingOrder(OrderSide side, double size, long quantity) throws StrategyException {
        Order order = new Order(targetInfo.getAccount(), targetInfo.getName(),
                targetInfo.getSymbol(), side, OrderType.MARKET, size, 0, quantity);
        logger.info("trying to make hedging order: {}", order);
        makeOrder(order);
        if (order.getOrderId() == null) {
            throw new StrategyException("failed to make hedging market order, order id is null");
        }
        logger.info("made hedging order: {}", order);
    }

    // 生成普通挂单，尽量成交，买一卖一价
    private Order generateBaseOrder(OrderSide side, long posQuantity) throws StrategyException {
        if (!side.equals(OrderSide.BUY) && !side.equals(OrderSide.SELL)) {
            throw new StrategyException("side error: " + side);
        }

        // 随机订单金额
        long orderQuantity;
        if (posQuantity == 0) {
            orderQuantity = strategyConfig.getInt("order_quantity", 100);
            if (strategyConfig.getBoolean("rand_order_quantity", true)) {
                orderQuantity += System.nanoTime() % orderQuantity;
            }
        } else {
            orderQuantity = posQuantity;
        }

        Order order = null;
        if (side.equals(OrderSide.BUY)) {
            DepthPrice depthPrice = accountActor.getBidDepthPrice(targetInfo);
            // 挂单，买一价价差配置
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            double price = depthPrice.price;

            price = price * (1 - bidPriceRate);
            price = price * (1 - accountActor.getTakerRate(targetInfo.getAccount()));
            price = price * (1 - accountActor.getMakerRate(sourceInfo.getAccount()));
            price = Utils.floor(price, sourceInfo.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sourceInfo.getSizePrecision());

            order = new Order(sourceInfo.getAccount(), sourceInfo.getName(), sourceInfo.getSymbol(),
                    side, OrderType.LIMIT, size, price, orderQuantity);
        } else {
            DepthPrice depthPrice = accountActor.getAskDepthPrice(targetInfo);
            // 挂单，卖一价价差配置
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = depthPrice.price;

            // 允许挂卖单
            price = price * (1 + askPriceRate);
            price = price * (1 + accountActor.getMakerRate(sourceInfo.getAccount()));
            price = price * (1 + accountActor.getTakerRate(targetInfo.getAccount()));
            price = Utils.ceil(price, sourceInfo.getPricePrecision());

            double size = Utils.round(orderQuantity * 1.0 / price, sourceInfo.getSizePrecision());

            order = new Order(sourceInfo.getAccount(), sourceInfo.getName(), sourceInfo.getSymbol(),
                    side, OrderType.LIMIT, size, price, orderQuantity);
        }

        // todo:: 检查订单是否满足下单条件，足够余额，价格范围等
        if (order.getQuantity() < strategyConfig.getLong("min_order_quantity", 17L)) {
            return null;
        }

        if (side.equals(OrderSide.BUY) && currentBidOrder != null && currentBidOrder.getOrderId() != null) {
            // 原有订单，检查是否需要撤单后重新挂单
            double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
            if (currentBidOrder.getPrice() > order.getPrice() ||
                    currentBidOrder.getPrice() < order.getPrice() * (1 - baseOrderCancelRate)) {
                CurrentOrder currentOrder = accountActor.cancelOrder(sourceInfo, currentBidOrder.getOrderId());
                logger.debug("cancel bid order: {}", currentOrder);
                currentBidOrder = null;
            } else {
                return null;
            }
        }

        if (side.equals(OrderSide.SELL) && currentAskOrder != null && currentAskOrder.getOrderId() != null) {
            // 原有订单，检查是否需要撤单后重新挂单
            double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
            if (currentAskOrder.getPrice() < order.getPrice() ||
                    currentAskOrder.getPrice() > order.getPrice() * (1 + baseOrderCancelRate)) {
                CurrentOrder currentOrder = accountActor.cancelOrder(sourceInfo, currentAskOrder.getOrderId());
                logger.debug("cancel ask order: {}", currentOrder);
                currentAskOrder = null;
            } else {
                return null;
            }
        }

        return order;
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

    private boolean checkOrderInterval() {
        long currentTime = System.currentTimeMillis();
        int orderInterval = strategyConfig.getInt("order_interval", 1000);
        if (currentTime - lastOrderTime < orderInterval) {
            return false;
        }
        lastOrderTime = currentTime;
        return true;
    }
}
