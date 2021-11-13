package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.common.util.StatisticsUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 永续合约盘口自动做市
 * reference:: https://docs.hummingbot.io/strategies/perpetual-market-making
 */
public class Strategy04 implements Strategy {
    private final static Logger logger = LoggerFactory.getLogger(Strategy04.class);

    private final StrategyConfig strategyConfig;
    private final Info0 info;
    private final AccountActor accountActor;

    // 采样统计过去一段时间的价格，假设回归到该价格
    // 长期价格会回归
    private double localMean;
    private double globalMean;
    private double localRsi;
    private double globalRsi;

    private Order bidOrder;
    private Order askOrder;
    private Order closeOrder;

    private double bidPeakPrice;
    private double askPeakPrice;

    // 1 -> 挂单撤单建仓，bidOrder != null, askOrder != null
    // 2 -> 仓位建立，跟踪价格等待触发profit rate，stopOrder != null
    // 3 -> 价格触发profit rate，等待触发 profit callback rate，peakPrice != 0
    // 4 -> 价格触发profit callback rate，平仓挂单，closeOrder != null

    public Strategy04(StrategyConfig strategyConfig,
                      AccountManager accountManager,
                      Info0 info) {
        this.strategyConfig = strategyConfig;
        this.info = info;

        this.accountActor = new AccountActor(accountManager);
    }

    @Override
    public void start() throws StrategyException {
        accountActor.cancelAllOrders(info);

        bidPeakPrice = 0;
        askPeakPrice = 0;

        logger.info("strategy {} started", strategyConfig);
    }

    @Override
    public void stop() throws StrategyException {
        accountActor.cancelAllOrders(info);

        logger.info("strategy {} stopped", strategyConfig.getName());
    }

    /**
     * // 单交易对合约做市
     * 1. 挂单建仓
     * 2. 平仓：达到一定 profit rate；
     *         通过回撤 profit callback rate 确定撤单
     */
    @Override
    public void run() throws StrategyException {
        // 获取当前仓位，若无仓位，则挂单建仓
        PositionValue position = accountActor.getPosition(info);

        int localRsiSize = strategyConfig.getInt("local_rsi_size", 14);
        int globalRsiSize = strategyConfig.getInt("global_rsi_size", 14);

        int localMASize = strategyConfig.getInt("local_ma_size", 6);
        int globalMASize = strategyConfig.getInt("global_ma_size", 26);

        List<KLineValue> kLineValues = accountActor.getKlineValues(info);
        localMean = StatisticsUtils.getMeanAverage(kLineValues, localMASize);
        globalMean = StatisticsUtils.getMeanAverage(kLineValues, globalMASize);
        localRsi = StatisticsUtils.getSMA_RSI(kLineValues, localRsiSize);
        globalRsi = StatisticsUtils.getSMA_RSI(kLineValues, globalRsiSize);

        logger.info("statistics, local mean: {}, global mean: {}, local rsi: {}, global rsi: {}",
                localMean, globalMean, localRsi, globalRsi);

        long minOrderQuantity = info.getLong("min_order_quantity", 13L);

        if (Math.abs(position.getQuantity()) < minOrderQuantity) {
            // 清理平仓订单，清理止损单
            cleanPositionState();

            // 认为空仓，挂单
            Order order1 = generateBaseOrder(OrderSide.BUY);
            if (order1 != null) {
                makeOrder(order1);
                if (order1.getOrderId() != null) {
                    bidOrder = order1;
                }
            }

            Order order2 = generateBaseOrder(OrderSide.SELL);
            if (order2 != null) {
                makeOrder(order2);
                if (order2.getOrderId() != null) {
                    askOrder = order2;
                }
            }

            bidPeakPrice = 0;
            askPeakPrice = 0;
        } else {
            // 清理base order
            cleanBaseState();

            // reset price
            if (bidPeakPrice == 0) {
                bidPeakPrice = position.getEntryPrice();
            }
            if (askPeakPrice == 0) {
                askPeakPrice = position.getEntryPrice();
            }

            // 仓位管理
            Order order = generateCloseOrder(position);
            if (order != null) {
                makeOrder(order);
                if (order.getOrderId() != null) {
                    closeOrder = order;
                }
            }
        }
    }


    private Order generateCloseOrder(PositionValue position) throws StrategyException {
        double profitRate = strategyConfig.getDouble("profit_rate", 0.01);
        double profitCallbackRate = strategyConfig.getDouble("profit_callback_rate", 0.005);
        double stopLossRate = strategyConfig.getDouble("stop_loss_rate", 0.01);
        // 根据变异系数调整回撤率（1 + a） * (1 + b) = 1 + a + b + ab, ab ~= 0时，简化计算
//        profitCallbackRate += localCoVariance;
//        stopLossRate += globalCoVariance;

        double delta = strategyConfig.getDouble("ma_delta", 0.0006);

        Order order = null;
        if (position.getQuantity() > 0) {
            // 多仓，以卖一价格参考平仓收益
            DepthPrice depthPrice = accountActor.getAskDepthPrice(info);

            double rsiBidMin = strategyConfig.getDouble("rsi_bid_min", 53);

            if (depthPrice.price < position.getEntryPrice() * (1 - stopLossRate) &&
                    localMean < globalMean * (1 + delta) && globalRsi < rsiBidMin) {
                // 止损单
                logger.info("stop loss, close long position, current price: {}, entry price: {}, " +
                                "local mean: {}, global mean: {}, global rsi: {}",
                        depthPrice.price, position.getEntryPrice(), localMean, globalMean, globalRsi);
                order = generateCloseOrder(position.getSize(), depthPrice.price);
            } else if (Math.max(askPeakPrice, depthPrice.price) > position.getEntryPrice() * (1 + profitRate)) {
                if (depthPrice.price > askPeakPrice) {
                    askPeakPrice = depthPrice.price;
                } else if (depthPrice.price < askPeakPrice * (1 - profitCallbackRate)) {
                    logger.info("close long position, current price: {}," +
                                    " ask peak price: {}, entry price: {}, callback rate: {}",
                            depthPrice.price, askPeakPrice, position.getEntryPrice(), profitCallbackRate);
                    order = generateCloseOrder(position.getSize(), depthPrice.price);
                }
            }
        } else {
            // 空仓，生成买单，以买一价格参考平仓收益
            DepthPrice depthPrice = accountActor.getBidDepthPrice(info);

            double rsiAskMax = strategyConfig.getDouble("rsi_ask_max", 47);

            if (depthPrice.price > position.getEntryPrice() * (1 + stopLossRate) &&
                    localMean > globalMean * (1 - delta) && globalRsi > rsiAskMax) {
                logger.info("stop loss, close short position, current price: {}, entry price: {}, " +
                                "local mean: {}, global mean: {}, global rsi: {}",
                        depthPrice.price, position.getEntryPrice(), localMean, globalMean, globalRsi);
                order = generateCloseOrder(position.getSize(), depthPrice.price);
            } else if (Math.min(bidPeakPrice, depthPrice.price) < position.getEntryPrice() * (1 - profitRate)) {
                if (depthPrice.price < bidPeakPrice) {
                    bidPeakPrice = depthPrice.price;
                } else if (depthPrice.price > bidPeakPrice * (1 + profitCallbackRate)) {
                    logger.info("close short position, current price: {}," +
                                    " bid peak price: {}, entry price: {}, callback rate: {}",
                            depthPrice.price, bidPeakPrice, position.getEntryPrice(), profitCallbackRate);
                    order = generateCloseOrder(position.getSize(), depthPrice.price);
                }
            }
        }

        if (order != null) {
            // 触发生成新的订单
            if (closeOrder == null || closeOrder.getOrderId() == null) {
                return order;
            } else {
                // 存在原先的订单
                CurrentOrder currentOrder = null;
                if (OrderSide.BUY.equals(order.getSide())) {
                    if (closeOrder.getPrice() > order.getPrice() ||
                            closeOrder.getPrice() < order.getPrice() *
                                    (1 - strategyConfig.getDouble("bid_cancel_rate", 0.001))) {
                        // 订单失效，取消订单
                        currentOrder = accountActor.cancelOrder(info, closeOrder.getOrderId());
                    }
                } else {
                    if (closeOrder.getPrice() < order.getPrice() ||
                            closeOrder.getPrice() > order.getPrice() *
                                    (1 + strategyConfig.getDouble("ask_cancel_rate", 0.001)) ) {
                        currentOrder = accountActor.cancelOrder(info, closeOrder.getOrderId());
                    }
                }

                if (currentOrder != null) {
                    // 原有订单取消
                    logger.info("cancelled close order: {}", currentOrder);
                    closeOrder = null;

                    if (currentOrder.getSize() == currentOrder.getFilledSize()) {
                        // 已经成交
                        return null;
                    }

                    return order;
                }

                // 原先订单价格有效，保留不取消时
                return null;
            }
        }

        return null;
    }

    private Order generateCloseOrder(double size, double price) {
        OrderSide side = size > 0 ? OrderSide.SELL : OrderSide.BUY;
        return new Order(info.getAccount(), info.getName(), info.getSymbol(), side, OrderType.LIMIT,
                Math.abs(size), price, Math.round(Math.abs(size * price)));
    }

    // 生成普通挂单，尽量成交，买一卖一价
    private Order generateBaseOrder(OrderSide side) throws StrategyException {
        if (!side.equals(OrderSide.BUY) && !side.equals(OrderSide.SELL)) {
            throw new StrategyException("side error: " + side);
        }

        // 随机订单金额
        long orderQuantity = strategyConfig.getInt("order_quantity", 100);
        if (strategyConfig.getBoolean("rand_order_quantity", true)) {
            orderQuantity += System.nanoTime() % orderQuantity;
        }

        double maxPriceRate = strategyConfig.getDouble("max_price_rate", 0.005);
        double delta = strategyConfig.getDouble("ma_delta", 0.0006);

        // 持仓在大多数时间并不产生资金费用，则假设无影响
//        double fundingRate = accountActor.getFundingRate(info);
        Order order = null;
        if (side.equals(OrderSide.BUY)) {
            DepthPrice depthPrice = accountActor.getBidDepthPrice(info);
            // 挂单，买一价价差配置
            double bidPriceRate = strategyConfig.getDouble("bid_price_rate", 0.0002);
            double price = depthPrice.price;

//            double rsiBidMax = strategyConfig.getDouble("rsi_bid_max", 60);
            double rsiBidMin = strategyConfig.getDouble("rsi_bid_min", 53);

            if (localMean < globalMean * (1 + delta) || localRsi < globalRsi || globalRsi < rsiBidMin) {
                // 此时不挂买单
                if (bidOrder != null) {
                    if (bidOrder.getOrderId() != null && bidOrder.getPrice() > price * (1 - maxPriceRate)) {
                        CurrentOrder currentOrder = accountActor.cancelOrder(info, bidOrder.getOrderId());
                        logger.info("cancel dangerous bid order: {}", currentOrder);
                    }
                    bidOrder = null;
                }
                return null;
            } else {
                // 此时满足挂买单的条件
                price = price * (1 - bidPriceRate);
                price = price * (1 - accountActor.getMakerRate(info));
                price = Utils.floor(price, info.getPricePrecision());

                double size = Utils.round(orderQuantity * 1.0 / price, info.getSizePrecision());

                order = new Order(info.getAccount(), info.getName(), info.getSymbol(),
                        side, OrderType.LIMIT, size, price, orderQuantity);
            }
        } else {
            DepthPrice depthPrice = accountActor.getAskDepthPrice(info);
            // 挂单，卖一价价差配置
            double askPriceRate = strategyConfig.getDouble("ask_price_rate", 0.0002);
            double price = depthPrice.price;

            double rsiAskMax = strategyConfig.getDouble("rsi_ask_max", 47);
//            double rsiAskMin = strategyConfig.getDouble("rsi_ask_min", 40);

            if (localMean > globalMean * (1 - delta) || localRsi > globalRsi || globalRsi > rsiAskMax) {
                // 此时不挂卖单
                if (askOrder != null) {
                    // 安全价格范围，可以不取消订单，保持挂单率
                    if (askOrder.getOrderId() != null && askOrder.getPrice() < price * (1 + maxPriceRate)) {
                        CurrentOrder currentOrder = accountActor.cancelOrder(info, askOrder.getOrderId());
                        logger.info("cancel dangerous ask order: {}", currentOrder);
                    }
                    askOrder = null;
                }
                return null;
            } else {
                // 允许挂卖单
                price = price * (1 + askPriceRate);
                price = price * (1 + accountActor.getMakerRate(info));
                price = Utils.ceil(price, info.getPricePrecision());

                double size = Utils.round(orderQuantity * 1.0 / price, info.getSizePrecision());

                order = new Order(info.getAccount(), info.getName(), info.getSymbol(),
                        side, OrderType.LIMIT, size, price, orderQuantity);
            }
        }

        // todo:: 检查订单是否满足下单条件，足够余额，价格范围等
        if (order.getQuantity() < info.getLong("min_order_quantity", 17L)) {
            return null;
        }

        if (side.equals(OrderSide.BUY) && bidOrder != null && bidOrder.getOrderId() != null) {
            // 原有订单，检查是否需要撤单后重新挂单
            double baseOrderCancelRate = strategyConfig.getDouble("bid_cancel_rate", 0.001);
            if (bidOrder.getPrice() > order.getPrice() ||
                    bidOrder.getPrice() < order.getPrice() * (1 - baseOrderCancelRate)) {
                CurrentOrder currentOrder = accountActor.cancelOrder(info, bidOrder.getOrderId());
                logger.debug("cancel base bid order: {}", currentOrder);
                bidOrder = null;
            } else {
                return null;
            }
        }

        if (side.equals(OrderSide.SELL) && askOrder != null && askOrder.getOrderId() != null) {
            // 原有订单，检查是否需要撤单后重新挂单
            double baseOrderCancelRate = strategyConfig.getDouble("ask_cancel_rate", 0.001);
            if (askOrder.getPrice() < order.getPrice() ||
                    askOrder.getPrice() > order.getPrice() * (1 + baseOrderCancelRate)) {
                CurrentOrder currentOrder = accountActor.cancelOrder(info, askOrder.getOrderId());
                logger.debug("cancel base ask order: {}", currentOrder);
                askOrder = null;
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

    private void cleanBaseState() throws StrategyException {
        // 取消现有订单
        if (bidOrder != null) {
            if (bidOrder.getOrderId() != null) {
                CurrentOrder currentOrder = accountActor.cancelOrder(info, bidOrder.getOrderId());
                logger.info("cancelled bid order: {}", currentOrder);
            }
            bidOrder = null;
        }
        if (askOrder != null && askOrder.getOrderId() != null) {
            if (askOrder.getOrderId() != null) {
                CurrentOrder currentOrder = accountActor.cancelOrder(info, askOrder.getOrderId());
                logger.info("cancelled ask order: {}", currentOrder);
            }
            askOrder = null;
        }
    }

    private void cleanPositionState() throws StrategyException {
        if (closeOrder != null) {
            if (closeOrder.getOrderId() != null) {
                CurrentOrder currentOrder = accountActor.cancelOrder(info, closeOrder.getOrderId());
                logger.info("cancelled close order: {}", currentOrder);
            }
            closeOrder = null;
        }
    }

}
