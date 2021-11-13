package org.eurekaka.bricks.server.executor;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.exception.OrderException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.model.ExOrder;
import org.eurekaka.bricks.server.store.ExOrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

/**
 * 聚合下单器，策略内使用时创建
 * limit order 尝试一定时间，或者价格变动一定比例，转用市价单
 */
public class FutureOrderExecutorV1 implements OrderExecutor {
    private final static Logger logger = LoggerFactory.getLogger(FutureOrderExecutorV1.class);

    // 默认单个limit order最大挂单金额
    private final static int DEFAULT_MAX_ORDER_QUANTITY = 26500;
    private final static int DEFAULT_MIN_ORDER_QUANTITY = 20;

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;
    private final ExOrderStore orderStore;

    private final AtomicBoolean exited;
    private final ExecutorService executorService;
    private final BlockingQueue<Long> queue;

    private final Map<String, String> config;
    private int maxOrderQuantity;
    private int minOrderQuantity;
    private int orderInterval;
    private int orderExpiredTime;
    private boolean addOne;
    private double orderPriceSpread;

    private final Map<Long, PlanOrder> limitPlanOrders;
    private final Map<Long, PlanOrder> marketPlanOrders;

    // order id -> current order
    private final Map<String, ExOrder> currentOrders;

    public FutureOrderExecutorV1(Map<String, String> config,
                                 AccountManager accountManager,
                                 InfoState<Info0, ?> infoState) {
        this(config, accountManager, infoState, new ExOrderStore());
    }

    public FutureOrderExecutorV1(Map<String, String> config,
                                 AccountManager accountManager,
                                 InfoState<Info0, ?> infoState,
                                 ExOrderStore orderStore) {
        this.config = config;
        this.accountManager = accountManager;
        this.infoState = infoState;
        this.orderStore = orderStore;

        this.limitPlanOrders = new ConcurrentHashMap<>();
        this.marketPlanOrders = new ConcurrentHashMap<>();
        this.currentOrders = new ConcurrentHashMap<>();
        this.queue = new LinkedBlockingQueue<>();
        exited = new AtomicBoolean(false);
        executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void start() {
        maxOrderQuantity = config.containsKey("max_order_quantity") ?
                Integer.parseInt(config.get("max_order_quantity")) :
                DEFAULT_MAX_ORDER_QUANTITY;

        minOrderQuantity = config.containsKey("min_order_quantity") ?
                Integer.parseInt(config.get("min_order_quantity")) :
                DEFAULT_MIN_ORDER_QUANTITY;

        orderInterval = config.containsKey("order_interval") ?
                Integer.parseInt(config.get("order_interval")) : 500;
        orderExpiredTime = config.containsKey("order_expired_time") ?
                Integer.parseInt(config.get("order_expired_time")) : 20000;

        addOne = config.containsKey("add_one") &&
                Boolean.parseBoolean(config.get("add_one"));

        // 买单价格比挂单上涨0.02%时
        orderPriceSpread = config.containsKey("order_price_spread") ?
                Double.parseDouble("order_price_spread") : 0.0002D;

        executorService.execute(new FutureProcessor());
    }

    @Override
    public void stop() {
        for (ExOrder currentOrder : currentOrders.values()) {
            try {
                cancelOrder(currentOrder);
            } catch (StoreException e) {
                logger.error("failed to cancel current order: {}", currentOrder);
            }
        }
        exited.set(true);
        executorService.shutdownNow();
        boolean succ;
        try {
            succ = executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
            if (!succ) {
                logger.error("executor failed to terminate normally");
            }
        } catch (InterruptedException e) {
            logger.error("executor interrupted");
        }
    }

    @Override
    public void makeOrder(String name, long quantity, long symbolPrice) {
        // 生成plan order，处理abs(quantity)太大或者太小的问题
        // 1. abs(quantity) < min quantity, discard
        // 2. abs(quantity) > max quantity, split
        if (quantity == 0) {
            return;
        }

        long qty = Math.abs(quantity);
        if (qty < minOrderQuantity) {
            return;
        }

        // 根据
//        List<Info0> infos = infoState.getInfoByName(name);
//        for (Info0 info : infos) {
//            if (qty < info.getInt("min_quantity", 0)) {
//                return;
//            }
//        }

        while (qty > 0) {
            long orderQty = qty < maxOrderQuantity ? qty : maxOrderQuantity - 100;
            qty = qty - orderQty;

            if (quantity < 0) {
                orderQty = -orderQty;
            }

            // symbol price 用于记录当前订单生成时的价格，有利于分析
            PlanOrder planOrder = new PlanOrder(0, name, orderQty, symbolPrice,
                    Math.abs(orderQty), System.currentTimeMillis(), System.currentTimeMillis());
            makeOrder(planOrder);
        }
    }

    @Override
    public void makeOrder(PlanOrder planOrder) {
        if (planOrder.getId() == 0) {
            // 存储 plan order
            try {
                orderStore.storePlanOrder(planOrder);
            } catch (StoreException e) {
                logger.error("failed to store plan order: {}", planOrder, e);
            }
        }
        if (orderExpiredTime == 0) {
            logger.info("make market planeOrder: {}", planOrder);
            marketPlanOrders.put(planOrder.getId(), planOrder);
        } else {
            logger.info("make limit planeOrder: {}", planOrder);
            limitPlanOrders.put(planOrder.getId(), planOrder);
        }
    }

    @Override
    public boolean makeOrder(Order order) {
        Exchange hedger = accountManager.getAccount(order.getAccount());
        if (hedger != null) {
            ExMessage<?> message = hedger.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
            if (message.getType() == ExMessage.ExMsgType.RIGHT) {
                String orderId = (String) message.getData();
                if (!orderId.startsWith("FAIL")) {
                    order.setOrderId((String) message.getData());
                    logger.info("made order: {}", order);
                    return true;
                } else if (orderId.equals("FAIL_OK")) {
                    order.setOrderId("FAIL_OK_" + order.getId());
                    logger.info("order fail ok: {}", order);
                    return true;
                }
            }
            logger.error("failed to make limit order {}", order, (Exception) message.getData());
        }
        return false;
    }

    @Override
    public void notify(Notification notification) {
        if (notification instanceof StrategyNotification) {
            StrategyNotification<?> not = (StrategyNotification<?>) notification;
            if (not.getData() instanceof Long) {
                this.queue.add((Long) not.getData());
            }
        }
    }

    @Override
    public boolean notBusy() {
        return limitPlanOrders.isEmpty() && marketPlanOrders.isEmpty() && currentOrders.isEmpty();
    }

    private List<ExOrder> generateOrders(PlanOrder planOrder, OrderType orderType) throws OrderException {
        List<ExOrder> orderList = new ArrayList<>();
        List<Info0> orderInfos = infoState.getInfoByName(planOrder.getName());
        for (Info0 orderInfo : orderInfos) {
            Exchange hedger = accountManager.getAccount(orderInfo.getAccount());
            if (hedger != null) {
                OrderSide side = OrderSide.valueOf(orderInfo.getProperty("side", "ALL"));
                if (planOrder.getQuantity() > 0 && Utils.buyAllowed(side) ||
                        planOrder.getQuantity() < 0 && Utils.sellAllowed(side)) {
                    // 判断允许下买单，找最低卖价
                    ExOrder order = null;
                    if (orderType.equals(OrderType.LIMIT)) {
                        order = generateLimitOrder(planOrder, orderInfo, hedger);
                    } else if (orderType.equals(OrderType.MARKET)) {
                        order = generateMarketOrder(planOrder, orderInfo, hedger);
                    }
                    if (order != null) {
                        orderList.add(order);
                    }
                }
            }
        }

        if (planOrder.getQuantity() > 0) {
            // 买单，按照价格从低到高排序
            orderList.sort(Comparator.comparing(ExOrder::getLastPrice));
        } else {
            // 卖单，按照价格从高到底排序
            orderList.sort(Comparator.comparing(ExOrder::getLastPrice).reversed());
        }
        if (logger.isDebugEnabled()) {
            for (ExOrder orderV2 : orderList) {
                logger.debug("generated order: {}", orderV2);
            }
        }

        return orderList;
    }

    private ExOrder generateLimitOrder(PlanOrder planOrder, Info0 orderInfo, Exchange hedger) throws OrderException {
        ExMessage<?> fundingRateMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair(orderInfo.getName(), orderInfo.getSymbol())));
        ExMessage<?> markUsdtMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT));
        ExMessage<?> askMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(planOrder.getName(), orderInfo.getSymbol(), DepthPricePair.ZERO_DEPTH_QTY)));
        ExMessage<?> bidMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(planOrder.getName(), orderInfo.getSymbol(), DepthPricePair.ZERO_DEPTH_QTY)));

        // 确保买一卖一价格有效
        if (hedger.isAlive() && fundingRateMsg.getType() == ExMessage.ExMsgType.RIGHT &&
                askMsg.getType() == ExMessage.ExMsgType.RIGHT &&
                bidMsg.getType() == ExMessage.ExMsgType.RIGHT) {
            double askPrice = ((DepthPrice) askMsg.getData()).price;
            double bidPrice = ((DepthPrice) bidMsg.getData()).price;

            if (planOrder.getQuantity() > 0) {
                // 挂买单，选择最低买价挂单
                double price = bidPrice;
                if (addOne) {
                    price += 1.0 / orderInfo.getPricePrecision();
                    if (price >= askPrice) {
                        price = bidPrice;
                    }
                }

                // 此处防止计算double导致精度问题
                price = Math.round(price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                double last_price = price / (double) markUsdtMsg.getData();
                last_price = last_price * (1 + (double) fundingRateMsg.getData());
                last_price = last_price * (1 + hedger.getMakerRate());
                last_price = Math.floor(last_price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                logger.trace("{} limit bid price: {}, usdt mark: {}, funding rate: {}, maker rate: {}",
                        hedger.getName(), price, markUsdtMsg.getData(),
                        fundingRateMsg.getData(), hedger.getMakerRate());

                // 计算size
                double size = planOrder.getLeftQuantity() * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }

                if (size == 0) {
                    throw new OrderException("generate size 0 limit order for: " +
                            planOrder.getName() + ", account: " + hedger.getName());
                }
                return new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.BUY, OrderType.LIMIT, size, price,
                        planOrder.getLeftQuantity(), last_price, planOrder.getId());
            } else {
                // 挂卖单，选择最高卖价挂单
//                double price = bidPrice + 1 / orderInfo.getPricePrecision();
//                if (price >= askPrice) {
//                    price = bidPrice;
//                }

                double price = askPrice;
                if (addOne) {
                    price -= 1.0 / orderInfo.getPricePrecision();
                    if (price <= bidPrice) {
                        price = askPrice;
                    }
                }
                price = Math.round(price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                double last_price = price / (double) markUsdtMsg.getData();
                last_price = last_price * (1 + (double) fundingRateMsg.getData());
                last_price = last_price * (1 - hedger.getMakerRate());
                last_price = Math.ceil(last_price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                logger.trace("{} limit ask price: {}, usdt mark: {}, funding rate: {}, maker rate: {}",
                        hedger.getName(), price, markUsdtMsg.getData(),
                        fundingRateMsg.getData(), hedger.getMakerRate());

                // 计算size
                double size = planOrder.getLeftQuantity() * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }

                if (size == 0) {
                    throw new OrderException("generate size 0 limit order for: " +
                            planOrder.getName() + ", account: " + hedger.getName());
                }
                return new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.SELL, OrderType.LIMIT, size, price,
                        planOrder.getLeftQuantity(), last_price, planOrder.getId());
            }
        }
        logger.warn("failed to generate limit order for hedger: {}", hedger.getName());
        return null;
    }

    private ExOrder generateMarketOrder(PlanOrder planOrder, Info0 orderInfo, Exchange hedger) throws OrderException {
        int infoDepth = orderInfo.getInt("depth", 79);
        if (planOrder.getQuantity() > 0) {
            // 买单，找最低卖价
            ExMessage<?> message = hedger.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                    new DepthPricePair(planOrder.getName(), orderInfo.getSymbol(), infoDepth)));
            ExMessage<?> fundingRateMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                    new SymbolPair(orderInfo.getName(), orderInfo.getSymbol())));
            ExMessage<?> markUsdtMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT));
            if (hedger.isAlive() && message.getType() == ExMessage.ExMsgType.RIGHT &&
                    fundingRateMsg.getType() == ExMessage.ExMsgType.RIGHT) {
                DepthPrice depthPrice = (DepthPrice) message.getData();
                double last_price = depthPrice.price;
                logger.trace("{} ask price: {}, usdt mark: {}, funding rate: {}, taker rate: {}",
                        hedger.getName(), last_price, markUsdtMsg.getData(),
                        fundingRateMsg.getData(), hedger.getTakerRate());
                last_price = last_price / (double) markUsdtMsg.getData();
                last_price = last_price * (1 + (double) fundingRateMsg.getData());
                last_price = last_price * (1 + hedger.getTakerRate());
                // 调整后实际价格，此价格仅作为比价依据
                last_price = Math.ceil(last_price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                // 多家数量精度可能不一致
                long quantity;
                // 避免落下过小的订单金额
                if (planOrder.getLeftQuantity() <= depthPrice.realQty + minOrderQuantity) {
                    quantity = planOrder.getLeftQuantity();
                } else {
                    // 避免过小的订单影响，不好完全匹配深度下单
                    quantity = depthPrice.realQty - minOrderQuantity;
                }

                double size = quantity * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }

                if (size == 0) {
                    throw new OrderException("generate size 0 market order for: " +
                            planOrder.getName() + ", account: " + hedger.getName());
                }
                return new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.BUY, OrderType.MARKET, size, depthPrice.price,
                        quantity, last_price, planOrder.getId());
            } else {
                logger.warn("{} no available ask depth price message: {}", hedger.getName(), message.getData());
            }
        } else {
            // 卖单，找最高买价
            ExMessage<?> message = hedger.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                    new DepthPricePair(planOrder.getName(), orderInfo.getSymbol(), infoDepth)));
            ExMessage<?> fundingRateMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                    new SymbolPair(orderInfo.getName(), orderInfo.getSymbol())));
            ExMessage<?> markUsdtMsg = hedger.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT));
            if (hedger.isAlive() && message.getType() == ExMessage.ExMsgType.RIGHT &&
                    fundingRateMsg.getType() == ExMessage.ExMsgType.RIGHT) {
                DepthPrice depthPrice = (DepthPrice) message.getData();
                double last_price = depthPrice.price;
                logger.trace("{} bid price: {}, usdt mark: {}, funding rate: {}, taker rate: {}",
                        hedger.getName(), last_price, markUsdtMsg.getData(),
                        fundingRateMsg.getData(), hedger.getTakerRate());
                last_price = last_price / (double) markUsdtMsg.getData();
                last_price = last_price * (1 + (double) fundingRateMsg.getData());
                last_price = last_price * (1 - hedger.getTakerRate());
                // 调整后实际价格
                last_price = Math.floor(last_price * orderInfo.getPricePrecision()) / orderInfo.getPricePrecision();

                long quantity;
                // 避免落下过小的订单金额
                if (planOrder.getLeftQuantity() <= depthPrice.realQty + minOrderQuantity) {
                    quantity = planOrder.getLeftQuantity();
                } else {
                    // 避免过小的订单影响，不好完全匹配深度下单
                    quantity = depthPrice.realQty - minOrderQuantity;
                }

                double size = quantity * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }
                if (size == 0) {
                    throw new OrderException("generate size 0 market order for: " +
                            planOrder.getName() + ", account: " + hedger.getName());
                }
                return new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.SELL, OrderType.MARKET, size, depthPrice.price,
                        quantity, last_price, planOrder.getId());
            } else {
                logger.warn("{} no available bid depth price message: {}", hedger.getName(), message.getData());
            }
        }

        return null;
    }

    /**
     * cancel order的同时更新订单最终状态
     * @param order 待取消订单
     * @return 订单状态更新成功
     */
    private boolean cancelOrder(ExOrder order) throws StoreException {
        // fail ok, 指该订单可能未实际生成，可以直接取消成功
        if (order.getOrderId().startsWith("FAIL_OK")) {
            return true;
        }

        Exchange hedger = accountManager.getAccount(order.getAccount());
        if (hedger != null) {
            ExMessage<?> message = hedger.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                    new CancelOrderPair(order.getName(), order.getSymbol(), order.getOrderId())));
            if (message.getType() == ExMessage.ExMsgType.RIGHT) {
                CurrentOrder currentOrder = (CurrentOrder) message.getData();
                double leftSize = order.getSize() - currentOrder.getFilledSize();
                orderStore.storeOrderResult(order.getOrderId(), leftSize, currentOrder.getStatus());
                PlanOrder planOrder = limitPlanOrders.get(order.getPlanId());
                if (planOrder == null) {
                    orderStore.updatePlanOrderLeftQuantity(0L, System.currentTimeMillis(), order.getPlanId());
                } else if (currentOrder.getFilledSize() > 0) {
                    // 四舍五入之后的size，重新乘以symbol price，可能与原本的quantity不同
                    long leftQuantity = planOrder.getLeftQuantity() -
                            Math.round(currentOrder.getFilledSize() * planOrder.getSymbolPrice() / PRECISION);
                    if (leftQuantity < minOrderQuantity) {
                        leftQuantity = 0;
                    }
                    if (leftSize <= 0.001) {
                        leftQuantity = 0;
                    }
                    planOrder.setLeftQuantity(leftQuantity);
                    planOrder.setUpdateTime(System.currentTimeMillis());
                    orderStore.updatePlanOrderLeftQuantity(planOrder.getLeftQuantity(),
                            planOrder.getUpdateTime(), order.getPlanId());
                }
                return true;
            }
            logger.error("failed to cancel order {}, error: {}", order, message.getData());
        }
        return false;
    }

    private void makeLimitOrder(PlanOrder planOrder) throws StoreException {
        boolean made = false;
        List<ExOrder> orders;
        try {
            orders = generateOrders(planOrder, OrderType.LIMIT);
        } catch (OrderException e) {
            // 该计划订单无法生成下单，移除
            logger.info("ignore making limit order", e);
            orderStore.updatePlanOrderLeftQuantity(0, System.currentTimeMillis(), planOrder.getId());
            limitPlanOrders.remove(planOrder.getId());
            return;
        }
        for (ExOrder order : orders) {
            orderStore.storeExOrder(order);
            if (makeOrder(order)) {
                // 挂单成功
                currentOrders.put(order.getOrderId(), order);
                orderStore.commitExOrder(order.getOrderId(), order.getId());
                made = true;
                break;
            } else {
                orderStore.commitExOrder(OrderResultValue.FAIL.name(), order.getId());
            }
        }
        if (!made) {
            String message = "limit order failed, id: " + planOrder.getId() +
                    ", symbol: " + planOrder.getName() +
                    ", quantity: " + planOrder.getQuantity() +
                    ", left quantity: " + planOrder.getLeftQuantity();
            MonitorReporter.report(String.valueOf(planOrder.getId()),
                    new ReportEvent(ReportEvent.EventType.HEDGING_MAKE_ORDER_FAILED,
                            ReportEvent.EventLevel.SERIOUS, message));
        }
    }

    private void makeMarketOrder(PlanOrder planOrder) throws StoreException {
        List<ExOrder> orders;
        try {
            orders = generateOrders(planOrder, OrderType.MARKET);
        } catch (OrderException e) {
            // 该计划订单无法生成下单，移除
            logger.info("ignore making market order", e);
            orderStore.updatePlanOrderLeftQuantity(0, System.currentTimeMillis(), planOrder.getId());
            marketPlanOrders.remove(planOrder.getId());
            return;
        }
        boolean made = false;
        for (ExOrder order : orders) {
            orderStore.storeExOrder(order);
            if (makeOrder(order)) {
                // 更新planOrder
                planOrder.setLeftQuantity(planOrder.getLeftQuantity() - order.getQuantity());
                planOrder.setUpdateTime(System.currentTimeMillis());
                orderStore.commitExOrder(order.getOrderId(), order.getId());
                orderStore.updatePlanOrderLeftQuantity(
                        planOrder.getLeftQuantity(), planOrder.getUpdateTime(), planOrder.getId());
                made = true;
                break;
            } else {
                orderStore.commitExOrder(OrderResultValue.FAIL.name(), order.getId());
            }
        }
        if (!made) {
            String msg = "market order failed, id: " + planOrder.getId() +
                    ", symbol: " + planOrder.getName() +
                    ", quantity: " + planOrder.getQuantity() +
                    ", left quantity: " + planOrder.getLeftQuantity();
            MonitorReporter.report(String.valueOf(planOrder.getId()), new ReportEvent(
                    ReportEvent.EventType.HEDGING_MAKE_ORDER_FAILED,
                    ReportEvent.EventLevel.SERIOUS, msg));
        }
    }

    private void updateCurrentOrder(ExOrder order) throws StoreException {
        // 1. 先根据价格判断是否需要撤单
        PlanOrder planOrder = limitPlanOrders.get(order.getPlanId());
        // 此时生成订单只为了拿到价格，若需要撤单再下单更新，则需要调整left quantity
        // 复用之前的生成订单方式
        List<ExOrder> lastOrders = null;
        try {
            lastOrders = generateOrders(planOrder, OrderType.LIMIT);
        } catch (OrderException e) {
            logger.error("failed to update current order, generate limit orders failed", e);
            return;
        }
        // 只需要跟第一家比较即可
        if (!lastOrders.isEmpty()) {
            boolean shouldUpdate = false;
            // 若之前订单不成功，则需要更新下单
            if (order.getOrderId().startsWith("FAIL_OK")) {
                // 需要重新下单
                shouldUpdate = true;
            }
            // 若是同一个账户，当前买一单价格比挂单低，或者卖一单价格比挂单高，则取消订单，此时挂单应当已经被吃完
            if (!shouldUpdate) {
                for (ExOrder lastOrder : lastOrders) {
                    if (lastOrder.getAccount().equals(order.getAccount())) {
                        if (order.getSide().equals(OrderSide.BUY) && lastOrder.getPrice() < order.getPrice() ||
                                order.getSide().equals(OrderSide.SELL) &&  lastOrder.getPrice() > order.getPrice()) {
                            shouldUpdate = true;
                            break;
                        }
                    }
                }
            }

            if (!shouldUpdate) {
                // 继续检查，是否已经非最佳挂单
                ExOrder bestOrder = lastOrders.get(0);
                // 对于买单方向
                // 同一家交易所，bestOrder 当前买一价格更低，不撤单
                // 同一家交易所，bestOrder 当前买一价格更高，撤单，更新left quantity, 重新下单
                // 不同交易所，bestOrder 买一价格更低，撤单，更新left quantity，重新挂单（此时可能已经成交）
                // 不同交易所，bestOrder 买一价格更高，撤单，更新left quantity，重新挂单
                // bestOrder.lastPrice > order.lastPrice 价格更高，则需要撤单，重新下单（此时不管是否同一家交易所，说明价格已经失效）
                // bestOrder.lastPrice <= order.lastPrice 不需要撤单，此时价格依然有效
                // 受到资金费率、手续费，计价货币的影响，直接使用
                // 价差拉动到一定程度时，我们才更新订单

                if (order.getSide().equals(OrderSide.BUY) &&
                        bestOrder.getLastPrice() > order.getLastPrice() * (1 + orderPriceSpread) &&
                        bestOrder.getPrice() > order.getPrice() * (1 + orderPriceSpread) ||
                        order.getSide().equals(OrderSide.SELL) &&
                                bestOrder.getLastPrice() < order.getLastPrice() * (1 - orderPriceSpread) &&
                                bestOrder.getPrice() < order.getPrice() * (1 - orderPriceSpread)) {
                    shouldUpdate = true;
                }
            }

            if (shouldUpdate) {
                if (cancelOrder(order)) {
                    currentOrders.remove(order.getOrderId());
                    // 已经取消订单，同时更新了left quantity
                    if (planOrder.getLeftQuantity() > 0) {
                        // 还未完全成交，继续挂单
                        makeLimitOrder(planOrder);
                    } else {
                        // 已经完全成交，remove该订单
                        limitPlanOrders.remove(planOrder.getId());
                    }
                }
            }
        }
    }

    private void shrink(List<PlanOrder> planOrders) throws StoreException {
        for (int i = 0; i < planOrders.size() - 1; i++) {
            PlanOrder order1 = planOrders.get(i);
            for (int j = i + 1; j < planOrders.size(); j++) {
                PlanOrder order2 = planOrders.get(j);

                if (order1.getName().equals(order2.getName())) {
                    // 确定两者方向相反
                    if (order1.getQuantity() > 0 && order2.getQuantity() < 0 ||
                            order1.getQuantity() < 0 && order2.getQuantity() > 0) {
                        // 两者都还有剩余金额
                        if (order1.getLeftQuantity() > 0 && order2.getLeftQuantity() > 0) {
                            long oldQty = 0;
                            if (order1.getLeftQuantity() > order2.getLeftQuantity()) {
                                oldQty = order2.getLeftQuantity();
                                order1.setLeftQuantity(order1.getLeftQuantity() - oldQty);
                                order2.setLeftQuantity(0);
                            } else {
                                oldQty = order1.getLeftQuantity();
                                order2.setLeftQuantity(order2.getLeftQuantity() - oldQty);
                                order1.setLeftQuantity(0);
                            }
                            order1.setUpdateTime(System.currentTimeMillis());
                            order2.setUpdateTime(System.currentTimeMillis());

                            OrderSide side1 = order1.getQuantity() > 0 ? OrderSide.BUY : OrderSide.SELL;
                            ExOrder exOrder1 = new ExOrder(null, order1.getName(), null,
                                    side1, OrderType.LIMIT, 0, 0, oldQty, 0, order1.getId());
                            // 在hedging_order_v2内增加shrink order的记录
                            orderStore.storeExOrder(exOrder1);
                            orderStore.commitExOrder("SHRINK", exOrder1.getId());
                            orderStore.updatePlanOrderLeftQuantity(
                                    order1.getLeftQuantity(), order1.getUpdateTime(), order1.getId());

                            OrderSide side2 = order2.getQuantity() > 0 ? OrderSide.BUY : OrderSide.SELL;
                            ExOrder exOrder2 = new ExOrder(null, order2.getName(), null,
                                    side2, OrderType.LIMIT, 0, 0, oldQty, 0, order2.getId());
                            orderStore.storeExOrder(exOrder2);
                            orderStore.commitExOrder("SHRINK", exOrder2.getId());
                            orderStore.updatePlanOrderLeftQuantity(
                                    order2.getLeftQuantity(), order2.getUpdateTime(), order2.getId());

                            logger.info("shrink order1: {}", exOrder1);
                            logger.info("shrink order2: {}", exOrder2);
                        }
                    }
                }
            }
        }
    }

    private class FutureProcessor implements Runnable {

        @Override
        public void run() {
            while (!exited.get()) {
                try {
                    Thread.sleep(orderInterval);

                    // 1. 处理订单完成消息（没啥用的感觉）
                    Long discardPlanOrderId;
                    while ((discardPlanOrderId = queue.peek()) != null) {
                        ExOrder found = null;
                        for (ExOrder order : currentOrders.values()) {
                            if (order.getPlanId() == discardPlanOrderId) {
                                // 该订单还有挂单
                                found = order;
                            }
                        }
                        if (found != null) {
                            cancelOrder(found);
                            currentOrders.remove(found.getOrderId());
                        }
                        limitPlanOrders.remove(discardPlanOrderId);
                        marketPlanOrders.remove(discardPlanOrderId);
                        orderStore.updatePlanOrderLeftQuantity(
                                0, System.currentTimeMillis(), discardPlanOrderId);
                        logger.info("remove plan order: {}", discardPlanOrderId);
                        queue.remove();
                    }

                    // 2. 查看当前订单，是否已经需要转入市价单处理
                    long currentTime = System.currentTimeMillis();
                    // 检查正在取消的订单，超过时间就移除，转入市价单
                    currentOrders.entrySet().removeIf(e -> {
                        long planOrderId = e.getValue().getPlanId();
                        PlanOrder planOrder = limitPlanOrders.get(planOrderId);
                        if (currentTime - planOrder.getStartTime() >= orderExpiredTime) {
                            // 先取消订单
                            // 保证limit订单能转移到market订单
                            try {
                                if (cancelOrder(e.getValue())) {
                                    // 将该订单转入 market 队列
                                    if (planOrder.getLeftQuantity() > 0) {
                                        marketPlanOrders.put(planOrderId, planOrder);
                                    }
                                    limitPlanOrders.remove(planOrderId);
                                    return true;
                                }
                            } catch (StoreException ex) {
                                logger.error("failed to cancel expired order: {}", e.getValue(), ex);
                            }
                        }
                        return false;
                    });

                    // 2.1. shrink订单（反向订单互相抵消）
                    List<PlanOrder> shrinkPlanOrders = new ArrayList<>();
                    Set<Long> currentPlanOrderIds = currentOrders.values().stream()
                            .map(ExOrder::getPlanId)
                            .collect(Collectors.toSet());
                    for (PlanOrder planOrder : limitPlanOrders.values()) {
                        if (!currentPlanOrderIds.contains(planOrder.getId())) {
                            shrinkPlanOrders.add(planOrder);
                        }
                    }
                    shrinkPlanOrders.addAll(marketPlanOrders.values());
                    shrink(shrinkPlanOrders);
                    limitPlanOrders.entrySet().removeIf(e -> e.getValue().getLeftQuantity() == 0);
                    marketPlanOrders.entrySet().removeIf(e -> e.getValue().getLeftQuantity() == 0);

                    // 3. 先处理市价单
                    for (PlanOrder planOrder : marketPlanOrders.values()) {
                        makeMarketOrder(planOrder);
                    }
                    marketPlanOrders.entrySet().removeIf(e -> e.getValue().getLeftQuantity() == 0);

                    // 4. 处理当前挂单，查看是否价格需要调整
                    List<ExOrder> currentOrderSnapshot = new ArrayList<>(currentOrders.values());
                    for (ExOrder order : currentOrderSnapshot) {
                        updateCurrentOrder(order);
                    }

                    // 5. 查看新收到的 limit plan order，下单
                    // 避免并发？？？
                    // 没有正在执行的limit plan order
                    Set<Long> limitPlanOrderIds = currentOrders.values().stream()
                            .map(ExOrder::getPlanId)
                            .collect(Collectors.toSet());
                    // 没有正在执行的名称（currentOrder支持多交易对管理）
                    // 支持反方向同时下单管理
                    Set<String> currentOrderSymbols = currentOrders.values().stream()
                            .map(Order::getName)
                            .collect(Collectors.toSet());
                    // market plan order 内也没有正在执行
                    Set<String> marketPlanOrderSymbols = marketPlanOrders.values().stream()
                            .map(PlanOrder::getName)
                            .collect(Collectors.toSet());

                    for (PlanOrder planOrder : limitPlanOrders.values()) {
                        String key = planOrder.getName();
                        if (!limitPlanOrderIds.contains(planOrder.getId()) &&
                                !currentOrderSymbols.contains(key) &&
                                !marketPlanOrderSymbols.contains(key)) {
                            if (planOrder.getLeftQuantity() > 0) {
                                makeLimitOrder(planOrder);
                                // 订单新下成功
                                planOrder.setStartTime(System.currentTimeMillis());
                                currentOrderSymbols.add(key);
                            }
                        }
                    }

                    limitPlanOrders.entrySet().removeIf(e -> e.getValue().getLeftQuantity() == 0);
                } catch (InterruptedException e) {
                    exited.set(true);
                    logger.debug("order processor stopped.");
                } catch (Throwable t) {
                    logger.error("failed to run order processor", t);
                    MonitorReporter.report(ReportEvent.EventType.HEDGING_LIMIT_PROCESSOR_FAILED.name(),
                            new ReportEvent(ReportEvent.EventType.HEDGING_LIMIT_PROCESSOR_FAILED,
                                    ReportEvent.EventLevel.SERIOUS,
                                    "limit order processor failed: " + t.getMessage()));
                }
            }
        }
    }


}
