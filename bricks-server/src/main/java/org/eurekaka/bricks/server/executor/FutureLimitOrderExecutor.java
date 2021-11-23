package org.eurekaka.bricks.server.executor;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.MonitorReporter;
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
 * future limit order executor
 * 执行期货的限价单，挂到市场上，等待一定的时间，如果成交则结束
 * 否则转入市价单成交
 *
 * 限价单价格可能随交易板买一卖一价格，动态调整
 */
public class FutureLimitOrderExecutor implements OrderExecutor {
    private final static Logger logger = LoggerFactory.getLogger(FutureLimitOrderExecutor.class);
    private static final long MAX_LIMIT_ORDER_QTY = 25600;

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;
    private final ExOrderStore orderStore;

    // plan order移除之前必须先移除currentOrders内的对应订单
    private final Map<Long, PlanOrder> planOrders;

    // order id -> current order
    private final Map<String, ExOrder> currentOrders;

    private final BlockingQueue<OrderNotification> queue;

    private final OrderExecutor marketOrderExecutor;

    private final ExecutorService executorService;
    private final int limitCheckInterval;
    // 经过expired time依然无法成交，此时开始撤单
    private final int limitExpiredTime;

    private final Object cleanLock;

    public FutureLimitOrderExecutor(Map<String, String> config,
                                    AccountManager accountManager,
                                    InfoState<Info0, ?> infoState,
                                    OrderExecutor marketOrderExecutor,
                                    ExOrderStore orderStore) {
        this.accountManager = accountManager;
        this.infoState = infoState;
        this.orderStore = orderStore;
        this.marketOrderExecutor = marketOrderExecutor;

        this.planOrders = new ConcurrentHashMap<>();
        this.currentOrders = new ConcurrentHashMap<>();
        this.queue = new LinkedBlockingQueue<>();

        this.limitCheckInterval = Integer.parseInt(config.getOrDefault(
                "limit_check_interval", "500"));
        this.limitExpiredTime = Integer.parseInt(config.getOrDefault(
                "limit_expired_time", "20000"));
        this.executorService = Executors.newSingleThreadExecutor();

        this.cleanLock = new Object();
    }

    @Override
    public void start() {
        if (marketOrderExecutor != null) {
            marketOrderExecutor.start();
        }

        this.executorService.execute(new FutureProcessor());

        try {
            for (PlanOrder planOrder : orderStore.queryPlanOrderNotFinished()) {
                this.makeOrder(planOrder);
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to recovery unfinished plan orders", e);
        }
    }

    @Override
    public void stop() {
        this.executorService.shutdownNow();
        try {
            this.executorService.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("exited future limit order executor abnormally");
        }
    }

    public void cleanOrder(long id) {
        synchronized (cleanLock) {
            if (id == 0) {
                int count = planOrders.size();
                this.planOrders.clear();
                logger.info("removed all plan orders, count: {}", count);
            } else if (planOrders.containsKey(id)) {
                planOrders.remove(id);
                logger.info("removed plan order: {}", id);
            }
        }
    }

    @Override
    public void makeOrder(String name, long quantity, long symbolPrice) {
        long qty = Math.abs(quantity);

        if (qty < 10) {
            return;
        }
        // 检查是否达到最低数量精度，必须全部达到精度
        boolean has_min_quantity = true;
        for (Info0 info : infoState.getInfoByName(name)) {
            if (info.getType() > 0) {
//            if (quantity * 1.0  * PRECISION / symbolPrice < 1.0 / orderInfo.getQuantityPrecision()) {
                if (symbolPrice * 1.0 / PRECISION / qty > info.getSizePrecision()) {
                    has_min_quantity = false;
                    break;
                }
            }
        }
        if (!has_min_quantity) {
            return;
        }

        while (qty > 0) {
            long orderQty = qty < MAX_LIMIT_ORDER_QTY ? qty : MAX_LIMIT_ORDER_QTY - 100;
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
//            throw new OrderException("plan order id should not be 0");
            // 存储 plan order
            try {
                orderStore.storePlanOrder(planOrder);
            } catch (StoreException e) {
                logger.error("failed to store plan order: {}", planOrder, e);
            }
        }
        logger.info("make limit planed orders: {}", planOrder);
        planOrders.put(planOrder.getId(), planOrder);
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
                    logger.info("made limit order: {}", order);
                    return true;
                }
//                else if (orderId.equals("FAIL_POC")) {
//                    order.setOrderId("FAIL_POC_" + order.getId());
//                    logger.info("limit order poc: {}", order);
//                    return true;
//                }
                else if (orderId.equals("FAIL_OK")) {
                    order.setOrderId("FAIL_OK_" + order.getId());
                    logger.info("limit order size small: {}", order);
                    return true;
                }
            }
            logger.error("failed to make limit order {}", order, (Exception) message.getData());
        }
        return false;
    }

    @Override
    public void notify(Notification notification) {
        if (notification instanceof OrderNotification) {

        }
//        ExOrder order = currentOrders.get(orderNotification.getId());
//        if (order != null) {
//            double leftSize = orderNotification.getSize() - orderNotification.getFilledSize();
//            if (Math.round(leftSize * orderNotification.getPrice()) < 17) {
//                // 订单已经完成，移除当前订单，但该方法可能产生并发问题，同时避免阻塞外部线程
//                this.queue.add(orderNotification);
//            }
//        }
//        logger.error("current order: {}", orderNotification);
    }

    // 此处不需要拆单，整体直接挂单即可，但需要依次比价，选择最优平台下单
    private List<ExOrder> generateOrders(PlanOrder planOrder) {
        List<ExOrder> orderList = new ArrayList<>();
        List<Info0> orderInfos = infoState.getInfoByName(planOrder.getName())
                .stream().filter(e -> e.getType() > 0).collect(Collectors.toList());
        for (Info0 orderInfo : orderInfos) {
            Exchange hedger = accountManager.getAccount(orderInfo.getAccount());
            if (hedger != null) {
                OrderSide side = OrderSide.valueOf(orderInfo.getProperty("side", "ALL"));
                if (planOrder.getQuantity() > 0 && (OrderSide.ALL.equals(side) || OrderSide.BUY.equals(side)) ||
                        planOrder.getQuantity() < 0 && (OrderSide.ALL.equals(side) || OrderSide.SELL.equals(side))) {
                    // 判断允许下买单，找最低卖价
                    ExOrder order = generateOrder(planOrder, orderInfo, hedger);
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

    private ExOrder generateOrder(PlanOrder planOrder, Info0 orderInfo, Exchange hedger) {
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
                // 如果需要促进成交率，可以设置 askPrice - 价格精度
//                double price = askPrice - 1 / orderInfo.getPricePrecision();
//                if (price <= bidPrice) {
//                    price = askPrice;
//                }
                double price = bidPrice + 1.0 / orderInfo.getPricePrecision();
                if (price >= askPrice) {
                    price = bidPrice;
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

                return size == 0 ? null :  new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.BUY, OrderType.LIMIT, size, price,
                        planOrder.getLeftQuantity(), last_price, planOrder.getId());
            } else {
                // 挂卖单，选择最高卖价挂单
//                double price = bidPrice + 1 / orderInfo.getPricePrecision();
//                if (price >= askPrice) {
//                    price = bidPrice;
//                }
                double price = askPrice - 1.0 / orderInfo.getPricePrecision();
                if (price <= bidPrice) {
                    price = askPrice;
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

                return size == 0 ? null :  new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.SELL, OrderType.LIMIT, size, price,
                        planOrder.getLeftQuantity(), last_price, planOrder.getId());
            }
        }
        logger.warn("failed to generate limit order for hedger: {}", hedger.getName());
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
                PlanOrder planOrder = planOrders.get(order.getPlanId());
                if (planOrder == null) {
                    orderStore.updatePlanOrderLeftQuantity(0L, System.currentTimeMillis(), order.getPlanId());
                } else if (currentOrder.getFilledSize() > 0) {
                    // 四舍五入之后的size，重新乘以symbol price，可能与原本的quantity不同
                    long leftQuantity = planOrder.getLeftQuantity() -
                            Math.round(currentOrder.getFilledSize() * planOrder.getSymbolPrice() / PRECISION);
                    if (leftQuantity < 17) {
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
        for (ExOrder order : generateOrders(planOrder)) {
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


    private class FutureProcessor implements Runnable {
        private final AtomicBoolean exited;
        private int lastSize;

        public FutureProcessor() {
            this.exited = new AtomicBoolean(false);
            this.lastSize = 0;
        }

        @Override
        public void run() {
            while (!exited.get()) {
                try {
                    Thread.sleep(limitCheckInterval);

                    synchronized (cleanLock) {
                        // 先处理订单完成的消息
                        OrderNotification finishedOrder;
                        while ((finishedOrder = queue.peek()) != null) {
                            ExOrder order = currentOrders.get(finishedOrder.getId());
                            if (order != null) {
                                // double check，完成该订单
                                orderStore.storeOrderResult(order.getOrderId(), 0,
                                        finishedOrder.getStatus().name());
                                orderStore.updatePlanOrderLeftQuantity(
                                        0, System.currentTimeMillis(), order.getPlanId());
                                currentOrders.remove(order.getOrderId());
                                planOrders.remove(order.getPlanId());
                            }
                            queue.remove();
                        }

                        // do shrink
                        // 先找一轮能够合并抵消的plan order，放在线程内部操作的目的为了避免加锁
                        if (planOrders.size() > lastSize) {
                            shrink();
                            planOrders.entrySet().removeIf(entry -> entry.getValue().getLeftQuantity() == 0);
                        }

                        // 如果当前订单的plan order已经移除，则取消对应的当前订单
                        currentOrders.entrySet().removeIf(e-> {
                            ExOrder order = e.getValue();
                            if (!planOrders.containsKey(order.getPlanId())) {
                                try {
                                    cancelOrder(order);
                                } catch (Exception ex) {
                                    logger.error("failed to cancel order: {}", order, ex);
                                }
                                return true;
                            }
                            return false;
                        });

                        // 2. 检查是否已经到最大挂单等待时间，转移到市价单，如果 expired time == 0，则禁止该功能
                        if (marketOrderExecutor != null && limitExpiredTime != 0) {
                            long currentTime = System.currentTimeMillis();

                            // 检查正在取消的订单，超过时间就移除，转入市价单
                            currentOrders.entrySet().removeIf(e -> {
                                long planOrderId = e.getValue().getPlanId();
                                PlanOrder planOrder = planOrders.get(planOrderId);
                                if (currentTime - planOrder.getStartTime() >= limitExpiredTime) {
                                    // 先取消订单
                                    // 保证limit订单能转移到market订单
                                    try {
                                        if (cancelOrder(e.getValue())) {
                                            // 将该订单转入 market 队列
                                            if (planOrder.getLeftQuantity() > 0) {
                                                marketOrderExecutor.makeOrder(planOrder);
                                            }
                                            planOrders.remove(planOrderId);
                                            return true;
                                        }
                                    } catch (StoreException ex) {
                                        logger.error("failed to cancel expired order: {}", e.getValue(), ex);
                                    }
                                }
                                return false;
                            });
                        }

                        // 3. 检查当前存在的订单的价格是否需要调整，若是，取消当前订单，挂新的订单
                        List<ExOrder> currentOrderSnapshot = new ArrayList<>(currentOrders.values());
                        for (ExOrder order : currentOrderSnapshot) {
                            updateCurrentOrder(order);
                        }

                        // 4. 寻找没有挂单的plan order，生成对应的订单，此时比价聚合下单
                        // 当前订单的plan id 集合
                        Set<Long> planOrderIds = currentOrders.values().stream()
                                .map(ExOrder::getPlanId)
                                .collect(Collectors.toSet());
                        Set<String> currentOrderSymbols = currentOrders.values().stream()
                                .map(ExOrder::getName)
                                .collect(Collectors.toSet());

                        for (PlanOrder planOrder : planOrders.values()) {
                            if (!planOrderIds.contains(planOrder.getId()) &&
                                    !currentOrderSymbols.contains(planOrder.getName())) {
                                // 没有挂上订单的plan order，并且当前订单内没有重复的symbol，避免并发下单
                                if (planOrder.getLeftQuantity() > 0) {
                                    makeLimitOrder(planOrder);
                                    // 订单新下成功
                                    planOrder.setStartTime(System.currentTimeMillis());
                                    currentOrderSymbols.add(planOrder.getName());
                                }
                            }
                        }

                        planOrders.entrySet().removeIf(e -> e.getValue().getLeftQuantity() == 0);

                        lastSize = planOrders.size();
                    }

                } catch (InterruptedException e) {
                    this.exited.set(true);
                    logger.info("limit order processor stopped.");
                } catch (Throwable t) {
                    logger.error("failed to run limit order processor", t);
                    MonitorReporter.report(ReportEvent.EventType.HEDGING_LIMIT_PROCESSOR_FAILED.name(),
                            new ReportEvent(ReportEvent.EventType.HEDGING_LIMIT_PROCESSOR_FAILED,
                                    ReportEvent.EventLevel.SERIOUS,
                                    "limit order processor failed: " + t.getMessage()));
                }
            }

            for (ExOrder order : currentOrders.values()) {
                try {
                    cancelOrder(order);
                } catch (StoreException e) {
                    logger.error("failed to cancel order: {}", order);
                }
            }
        }

        public void stop() {
            this.exited.set(true);
        }

        private void updateCurrentOrder(ExOrder order) throws StoreException {
            // 1. 先根据价格判断是否需要撤单
            PlanOrder planOrder = planOrders.get(order.getPlanId());
            // 此时生成订单只为了拿到价格，若需要撤单再下单更新，则需要调整left quantity
            // 复用之前的生成订单方式
            List<ExOrder> lastOrders = generateOrders(planOrder);
            // 只需要跟第一家比较即可
            if (!lastOrders.isEmpty()) {
                ExOrder bestOrder = lastOrders.get(0);
                // 对于买单方向
                // 同一家交易所，bestOrder 当前买一价格更低，不撤单
                // 同一家交易所，bestOrder 当前买一价格更高，撤单，更新left quantity, 重新下单
                // 不同交易所，bestOrder 买一价格更低，撤单，更新left quantity，重新挂单（此时可能已经成交）
                // 不同交易所，bestOrder 买一价格更高，撤单，更新left quantity，重新挂单
                // bestOrder.lastPrice > order.lastPrice 价格更高，则需要撤单，重新下单（此时不管是否同一家交易所，说明价格已经失效）
                // bestOrder.lastPrice <= order.lastPrice 不需要撤单，此时价格依然有效
                // 受到资金费率、手续费，计价货币的影响，直接使用

                if (order.getSide().equals(OrderSide.BUY) && bestOrder.getLastPrice() > order.getLastPrice() &&
                        bestOrder.getPrice() > order.getPrice() ||
                        order.getSide().equals(OrderSide.SELL) && bestOrder.getLastPrice() < order.getLastPrice() &&
                                bestOrder.getPrice() < order.getPrice()) {
                    if (cancelOrder(order)) {
                        currentOrders.remove(order.getOrderId());
                        // 已经取消订单，同时更新了left quantity
                        if (planOrder.getLeftQuantity() > 0) {
                            // 还未完全成交，继续挂单
                            makeLimitOrder(planOrder);
                        } else {
                            // 已经完全成交，remove该订单
                            planOrders.remove(planOrder.getId());
                        }
                    }
                }
            }
        }

        private void shrink() throws StoreException {
            List<ExOrder> shrinkOrders = new ArrayList<>();
            List<Long> planOrderIds = new ArrayList<>(planOrders.keySet());
            for (int i = 0; i < planOrderIds.size() - 1; i++) {
                PlanOrder order1 = planOrders.get(planOrderIds.get(i));

                for (int j = i + 1; j < planOrderIds.size(); j++) {
                    PlanOrder order2 = planOrders.get(planOrderIds.get(j));

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
                                shrinkOrders.add(new ExOrder(null, order1.getName(), null,
                                        side1, OrderType.LIMIT, 0, 0, oldQty, 0, order1.getId()));

                                OrderSide side2 = order2.getQuantity() > 0 ? OrderSide.BUY : OrderSide.SELL;
                                shrinkOrders.add(new ExOrder(null, order2.getName(), null,
                                        side2, OrderType.LIMIT, 0, 0, oldQty, 0, order2.getId()));
                            }
                        }
                    }
                }
            }

            for (ExOrder shrinkOrder : shrinkOrders) {
                // update order store
                // 在hedging_order_v2内增加shrink order的记录
                orderStore.storeExOrder(shrinkOrder);
                orderStore.commitExOrder(shrinkOrder.getOrderId(), shrinkOrder.getId());

                logger.info("shrink order, plan order id: {}, symbol: {}, side: {}, quantity: {}",
                        shrinkOrder.getPlanId(), shrinkOrder.getName(),
                        shrinkOrder.getSide(), shrinkOrder.getQuantity());
                // update hedging_plan_order
                PlanOrder planOrder = planOrders.get(shrinkOrder.getPlanId());
                orderStore.updatePlanOrderLeftQuantity(
                        planOrder.getLeftQuantity(), planOrder.getUpdateTime(), planOrder.getId());
            }
        }
    }

}
