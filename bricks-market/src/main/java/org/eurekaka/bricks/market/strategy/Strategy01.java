package org.eurekaka.bricks.market.strategy;

import org.apache.commons.math3.distribution.LogNormalDistribution;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 自动生成挂单策略，非盘口，价差较大，假设对冲市场深度足够，在离盘口1%左右的地方挂单，实时对冲
 * 优化：缩小价差，根据目标盘口最优选择挂单价格，保证做市盘口价格优化
 * 此策略侧重于服务做市商，需要跨市场对冲，若注重套利，则不需要每次主动挂撤单
 * 对冲策略： 1. 仓位数量变化消息推送时对冲（即时对冲）
 *          2. 成交订单消息推送立刻对冲（即时对冲）
 *          3. 定时轮询仓位数量，根据前后差值对冲（定时对冲，若量太大，拆分数量）
 */
public class Strategy01 implements Strategy {
    private final static Logger logger = LoggerFactory.getLogger(Strategy01.class);

    private final StrategyConfig strategyConfig;
    private final BrickContext brickContext;

    // 本地订单记录，便于不同策略下单分类
    // order id -> order
    private final Map<String, CurrentOrder> bidOrders;
    private final Map<String, CurrentOrder> askOrders;

    private LogNormalDistribution normalDistribution;
    // 接收完成订单，每次循环时，累积订单处理
    private Info0 marketInfo;
    private Exchange marketEx;
    private boolean debug;
    // 对冲模式：trade, position
//    private final String hedgingMode;

    public Strategy01(BrickContext brickContext,
                      StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.bidOrders = new ConcurrentHashMap<>();
        this.askOrders = new ConcurrentHashMap<>();
    }

    @Override
    public void start() throws StrategyException {
        // 必须有两个以上的交易对存在，跨市场对冲
        List<Info0> infos = brickContext.getInfoByName(strategyConfig.getInfoName());
        if (infos.size() < 2) {
            throw new StrategyException("less than 2 infos found: " + infos);
        }

        String targetAccount = strategyConfig.getProperty("target_account");
        if (targetAccount == null) {
            throw new StrategyException("strategy config target_account is null");
        }

        for (Info0 info : infos) {
            if (targetAccount.equals(info.getAccount())) {
                marketInfo = info;
            }
        }
        if (marketInfo == null) {
            throw new StrategyException("no market info found");
        }

        marketEx = brickContext.getAccount(targetAccount);
        if (marketEx == null) {
            throw new StrategyException("no target_account available: " + targetAccount);
        }

        // 是否测试
        debug = strategyConfig.getBoolean("debug", false);
    }

    @Override
    public void stop() throws StrategyException {
        logger.info("strategy {} exited", strategyConfig.getName());
    }

    /**
     * 做市基本步骤
     * 1. 获取当前所有订单
     * 2. 分析当前订单，计算需要撤销的订单与数量
     * 3. 撤销旧的订单
     * 4. 生成新的订单，补全订单
     *
     * @throws StrategyException 策略运行失败
     */
    @Override
    public void run() throws StrategyException {
        // 检查可变参数的衍生对象是否需要更新
        double mu = strategyConfig.getDouble("mu", 4.0);
        double sigma = strategyConfig.getDouble("sigma", 1.0);
        // 更新参数
        if (normalDistribution == null ||
                normalDistribution.getScale() != mu ||
                normalDistribution.getShape() != sigma) {
            normalDistribution = new LogNormalDistribution(mu, sigma);
        }

        List<Info0> infos = brickContext.getInfoByName(strategyConfig.getInfoName())
                .stream().filter(e -> !e.getAccount().equals(marketInfo.getAccount()))
                .collect(Collectors.toList());
        if (infos.isEmpty()) {
            throw new StrategyException("no hedging info found: " + strategyConfig.getInfoName());
        }

        if (!debug) {
            updateCurrentOrders();
        }

        // 更新买单
        runOrders(1, infos);
        // 更新卖单
        runOrders(2, infos);

        // mock 订单？
        if (strategyConfig.getBoolean("mock_order", false)) {
            //
        }
    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        // 1. 接收订单成交记录/订单状态变更
//        if (notification instanceof OrderNotification) {
//            OrderNotification order = (OrderNotification) notification;
//            if (order.getSize() == order.getFilledSize()) {
//                if (order.getSide().equals(OrderSide.BUY)) {
//                    bidOrders.remove(order.getId());
//                } else {
//                    askOrders.remove(order.getId());
//                }
//                logger.debug("remove order: {}", order);
//            }
//        }
    }

    private void updateCurrentOrders() throws StrategyException {
        bidOrders.clear();
        askOrders.clear();

        ExMessage<?> currentOrdersMsg = marketEx.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair(marketInfo.getName(), marketInfo.getSymbol(), 0)));
        if (currentOrdersMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            logger.error("failed to get current orders. name: {}, symbol: {}",
                    marketInfo.getName(), marketInfo.getSymbol(), (Exception) currentOrdersMsg.getData());
            throw new StrategyException("failed to get strategy started.", (Exception) currentOrdersMsg.getData());
        }
        List<CurrentOrder> currentOrders = (List<CurrentOrder>) currentOrdersMsg.getData();
        for (CurrentOrder currentOrder : currentOrders) {
            if (currentOrder.getSide().equals(OrderSide.BUY)) {
                bidOrders.put(currentOrder.getId(), currentOrder);
            } else if (currentOrder.getSide().equals(OrderSide.SELL)) {
                askOrders.put(currentOrder.getId(), currentOrder);
            }
        }
    }

    private void runOrders(int type, List<Info0> infos) {
        if (type != 1 && type != 2) {
            logger.error("unknown order type, 1 or 2 supported");
            return;
        }
        // 1 获取当前所有订单
        List<CurrentOrder> currentOrders = new ArrayList<>(type == 1 ? bidOrders.values() : askOrders.values());
        int currentOrdersCount = currentOrders.size();

        // 2. 筛选需要删除的订单，价格深度不符合目标对冲深度
        // 找到所有市场交易对的优秀价格，检查是否全部符合（安全优先）
        // fixme:: 应当使用累积深度，此处简便处理先
        double goodPrice = type == 1 ? Double.MAX_VALUE : 0;
        boolean foundGoodPrice = false;
        for (Info0 info : infos) {
            int depthQty = info.getInt(Info0.DEPTH_QTY_KEY, 113);
            double price = getGoodPrice(info, type, depthQty);
            if (price > 0) {
                if (type == 1 && price < goodPrice) {
                    goodPrice = price;
                    foundGoodPrice = true;
                } else if (type == 2 && price > goodPrice) {
                    goodPrice = price;
                    foundGoodPrice = true;
                }
            }
        }
        // fixme:: market exchange的买一卖一价格比较，避免挂单冲突?? 不需要，直接吃单
        if (!foundGoodPrice) {
            return;
        }

        List<CurrentOrder> cancelOrders = new ArrayList<>();
        for (CurrentOrder currentOrder : currentOrders) {
            if (type == 1 && currentOrder.getPrice() >= goodPrice ||
                    type == 2 && currentOrder.getPrice() <= goodPrice) {
                cancelOrders.add(currentOrder);
            }
        }

        // 移除这部分订单
        currentOrders.removeAll(cancelOrders);
        // 其他订单内随机寻找1～2个订单取消，产生交易板流动性
        int cancelCount = strategyConfig.getInt("cancel_count", 2);
        for (int i = cancelOrders.size(); i < cancelCount; i++) {
            if (currentOrders.size() > 0) {
                int randomIndex = (int) (System.nanoTime() % currentOrders.size());
                cancelOrders.add(currentOrders.get(randomIndex));
                currentOrders.remove(randomIndex);
            }
        }

        // 3. 撤销订单
        for (CurrentOrder cancelOrder : cancelOrders) {
            if (debug) {
                if (cancelOrder.getSide().equals(OrderSide.BUY)) {
                    bidOrders.remove(cancelOrder.getId());
                } else {
                    askOrders.remove(cancelOrder.getId());
                }
                logger.info("remove order: {}", cancelOrder);
                continue;
            }
            ExMessage<?> orderMsg = marketEx.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                    new CancelOrderPair(cancelOrder.getName(), cancelOrder.getSymbol(), cancelOrder.getId())));
            if (orderMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                logger.error("failed to cancel order: {}", cancelOrder, (Exception) orderMsg.getData());
            }
//            else {
//                if (type == 1) {
//                    bidOrders.remove(cancelOrder.getId());
//                } else {
//                    askOrders.remove(cancelOrder.getId());
//                }
//            }
        }

        // 4. 计算需要添加的订单数量，生成对应的订单
        // fixme:: 风险控制，如果不允许挂单则直接返回
        OrderSide side = OrderSide.valueOf(marketInfo.getProperty(Info0.ORDER_SIDE_KEY, "ALL"));
        if (type == 1 && Utils.buyAllowed(side) ||
                type == 2 && Utils.sellAllowed(side)) {
            int totalCount = strategyConfig.getInt("total_order_count", 5);
            int count = totalCount - currentOrders.size();
            // 如果现有订单总数多于计划总订单数，则不下单（服务端积压）
            if (currentOrdersCount <= totalCount) {
                // 补充订单数量
                List<Order> orders = genOrders(goodPrice, type, count);
                for (Order order : orders) {
                    if (debug) {
                        logger.info("made order: {}", order);
                        order.setOrderId("id" + System.nanoTime());
                        putOrder(order);
                        continue;
                    }
                    ExMessage<?> orderMsg = marketEx.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
                    if (orderMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                        logger.error("failed to make order: {}", order, (Exception) orderMsg.getData());
                    }
//                    else {
//                        order.setOrderId((String) orderMsg.getData());
//                        putOrder(order);
//                    }
                }
            }
        }
    }

    private void putOrder(Order order) {
        CurrentOrder currentOrder = new CurrentOrder(order.getOrderId(), order.getName(), order.getSymbol(),
                order.getSide(), OrderType.LIMIT, order.getSize(), order.getPrice(), 0);
        if (order.getSide().equals(OrderSide.BUY)) {
            bidOrders.put(order.getOrderId(), currentOrder);
        } else {
            askOrders.put(order.getOrderId(), currentOrder);
        }
    }

    private double getGoodPrice(Info<?> info, int type, int depthQty) {
        // 获取价差，默认0.002
        double minPriceRate = strategyConfig.getDouble("min_price_rate", 0.004);
        Exchange ex = brickContext.getAccount(info.getAccount());
        ExMessage<?> fundingRateMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair(info.getName(), info.getSymbol())));
        // 获取做市账户资金费率
        ExMessage<?> fundingRateMsg2 = marketEx.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair(this.marketInfo.getName(), this.marketInfo.getSymbol())));
        ExMessage<?> markUsdtMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT));
        if (fundingRateMsg.getType().equals(ExMessage.ExMsgType.RIGHT) &&
                fundingRateMsg2.getType().equals(ExMessage.ExMsgType.RIGHT) &&
                markUsdtMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
            double rate = (double) fundingRateMsg.getData();
            double marketRate = (double) fundingRateMsg2.getData();
            double markRate = (double) markUsdtMsg.getData();
            if (type == 1) {
                // bid
                ExMessage<?> depthMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                        new DepthPricePair(info.getName(), info.getSymbol(), depthQty)));
                if (depthMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                    DepthPrice depthPrice = (DepthPrice) depthMsg.getData();
                    // 考虑手续费
                    double goodPrice = depthPrice.price / (1 + ex.getTakerRate());
                    goodPrice /= (1 + marketEx.getTakerRate());
                    goodPrice /= (1 + minPriceRate);
                    // 价格转换， USD/USDT, BUSD/USDT, etc...
                    goodPrice /= markRate;
                    // 需要根据funding rate调整，买单，rate < 0时，鼓励买入
                    goodPrice /= (1 + rate);
                    goodPrice /= (1 + marketRate);
                    // 价格精度处理
                    return Math.floor(goodPrice * this.marketInfo.getPricePrecision()) / this.marketInfo.getPricePrecision();
                }
            } else {
                // ask
                ExMessage<?> depthMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                        new DepthPricePair(info.getName(), info.getSymbol(), depthQty)));
                if (depthMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                    DepthPrice depthPrice = (DepthPrice) depthMsg.getData();
                    // 考虑卖单手续费
                    double goodPrice = depthPrice.price * (1 + ex.getTakerRate());
                    goodPrice *= (1 + marketEx.getTakerRate());
                    goodPrice *= (1 + minPriceRate);
                    goodPrice /= markRate;
                    // 需要根据funding rate调整，卖单，rate > 0时，鼓励卖出
                    goodPrice /= (1 + rate);
                    goodPrice /= (1 + marketRate);
                    // 价格精度处理
                    return Math.ceil(goodPrice * this.marketInfo.getPricePrecision()) / this.marketInfo.getPricePrecision();
                }
            }
        }

        return 0;
    }

    /**
     * 根据当前订单，生成count数量的新订单补充
     * @param type 订单方向，1/2
     * @param count 待生成订单数量
     * @return 订单个数
     */
    private List<Order> genOrders(double goodPrice, int type, int count) {
        // 1. 先生成一个good price 价格的订单
        List<Order> orders = new ArrayList<>();
        OrderSide side = type == 1 ? OrderSide.BUY : OrderSide.SELL;
        double minSize = goodPrice / marketInfo.getSizePrecision();
        double size = Utils.round((normalDistribution.sample() + minSize) / goodPrice,
                marketInfo.getSizePrecision());
        orders.add(new Order(marketInfo.getAccount(), marketInfo.getName(), marketInfo.getSymbol(),
                side, OrderType.LIMIT, size, goodPrice, Math.round(goodPrice * size)));

        double minPriceRate = strategyConfig.getDouble("min_price_rate", 0.002);
        double maxPriceRate = strategyConfig.getDouble("max_price_rate", 0.2);
        long priceCount = Math.round(maxPriceRate / minPriceRate);
        long priceCnt = Math.round(goodPrice * marketInfo.getPricePrecision());

        // 生成剩下的count - 1个订单
        for (int i = 0; i < count - 1; i++) {
            size = Utils.round((normalDistribution.sample() + minSize) / goodPrice,
                    marketInfo.getSizePrecision());
            double ratio;
            if (priceCnt < priceCount) {
                ratio = (System.nanoTime() % priceCnt + 1) * 1.0 / priceCnt;
            } else {
                ratio = (System.nanoTime() % priceCount + 1) * 1.0 / priceCnt;
            }
            double price;
            if (type == 1) {
                price = Utils.floor(goodPrice * (1 - ratio), marketInfo.getPricePrecision());
            } else {
                price = Utils.ceil(goodPrice * (1 + ratio), marketInfo.getPricePrecision());
            }
            orders.add(new Order(marketInfo.getAccount(), marketInfo.getName(), marketInfo.getSymbol(),
                    side, OrderType.LIMIT, size, price, Math.round(goodPrice * size)));
        }

        return orders;
    }

}
