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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class FutureMarketOrderExecutor implements OrderExecutor {
    private final static Logger logger = LoggerFactory.getLogger(FutureMarketOrderExecutor.class);

    // ??? config 可动态配置？？
    private static final int LOWEST_ORDER_QUANTITY = 20;
    private static final int ORDER_MAKER_INTERVAL = 500;

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> state;
    private final ExOrderStore orderStore;

    private final Map<Long, PlanOrder> planOrders;
    private final ExecutorService executorService;

    public FutureMarketOrderExecutor(AccountManager accountManager,
                                     InfoState<Info0, ?> state,
                                     ExOrderStore orderStore) {
        this.accountManager = accountManager;
        this.state = state;
        this.orderStore = orderStore;

        this.planOrders = new ConcurrentHashMap<>();

        executorService = Executors.newFixedThreadPool(1);
    }

    @Override
    public void start() {
        executorService.execute(new MarketOrderMaker());
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("future market order executor exited abnormally");
        }
    }

    @Override
    public void makeOrder(String name, long quantity, long symbolPrice) {
        if (quantity == 0) {
            return;
        }
        // symbol price 用于记录当前订单生成时的价格，有利于分析
        PlanOrder planOrder = new PlanOrder(0, name, quantity, symbolPrice,
                Math.abs(quantity), System.currentTimeMillis(), System.currentTimeMillis());
        makeOrder(planOrder);
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
        logger.info("make market planed orders: {}", planOrder);
        planOrders.put(planOrder.getId(), planOrder);

        // 1. 生成订单列表，便于第一个下单失败时，继续第二个下单
        // 2. 下单
        // 3. 下单成功后更新plan order
    }


    @Override
    public boolean makeOrder(Order order) {
        Exchange hedger = accountManager.getAccount(order.getAccount());
        if (hedger != null) {
            ExMessage<?> message = hedger.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
            if (message.getType() == ExMessage.ExMsgType.RIGHT) {
                String orderId = (String) message.getData();
                // 更新plan order
                order.setOrderId(orderId);
                logger.info("made order: {}", order);
                return true;
            } else {
                logger.error("failed to make order: {}, error: {}", order, message.getData());
            }
        }

        return false;
    }


    /**
     * 根据plan order，依据当前可下单的对冲账户，拆分下单
     * @param planOrder plan order, 以内部left quantity为准生成下一个订单
     * @return 生成待下单的order
     */
    private List<ExOrder> generateOrders(PlanOrder planOrder) {
        List<ExOrder> orderList = new ArrayList<>();

        List<Info0> orderInfos = state.getInfoByName(planOrder.getName())
                .stream().filter(e -> e.getType() > 0).collect(Collectors.toList());
        // 保持优先级???
        for (Info0 orderInfo : orderInfos) {
            Exchange ex = accountManager.getAccount(orderInfo.getAccount());
            if (ex != null) {
                OrderSide side = OrderSide.valueOf(orderInfo.getProperty("side", "ALL"));
                if (planOrder.getQuantity() > 0 && (OrderSide.ALL.equals(side) || OrderSide.BUY.equals(side)) ||
                        planOrder.getQuantity() < 0 && (OrderSide.ALL.equals(side) || OrderSide.SELL.equals(side))) {
                    // 判断允许下买单，找最低卖价
                    ExOrder order = generateOrder(planOrder, orderInfo, ex);
                    if (order != null) {
                        orderList.add(order);
                    }
                }
            }
        }
        if (planOrder.getQuantity() > 0) {
            orderList.sort(Comparator.comparing(ExOrder::getLastPrice));
        } else {
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
        int infoDepth = orderInfo.getInt("depth", 213);
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
                if (planOrder.getLeftQuantity() <= depthPrice.realQty + LOWEST_ORDER_QUANTITY) {
                    quantity = planOrder.getLeftQuantity();
                } else {
                    // 避免过小的订单影响，不好完全匹配深度下单
                    quantity = depthPrice.realQty - LOWEST_ORDER_QUANTITY;
                }

                double size = quantity * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }

                return size == 0 ? null :  new ExOrder(orderInfo.getAccount(), planOrder.getName(),
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
                if (planOrder.getLeftQuantity() <= depthPrice.realQty + LOWEST_ORDER_QUANTITY) {
                    quantity = planOrder.getLeftQuantity();
                } else {
                    // 避免过小的订单影响，不好完全匹配深度下单
                    quantity = depthPrice.realQty - LOWEST_ORDER_QUANTITY;
                }

                double size = quantity * 1.0 * PRECISION / planOrder.getSymbolPrice();
                size = Math.round(size * orderInfo.getSizePrecision());
                if (orderInfo.getSizePrecision() < 1) {
                    size = size * Math.round(1.0 / orderInfo.getSizePrecision());
                } else {
                    size = size / orderInfo.getSizePrecision();
                }

                return size == 0 ? null : new ExOrder(orderInfo.getAccount(), planOrder.getName(),
                        orderInfo.getSymbol(), OrderSide.SELL, OrderType.MARKET, size, depthPrice.price,
                        quantity, last_price, planOrder.getId());
            } else {
                logger.warn("{} no available bid depth price message: {}", hedger.getName(), message.getData());
            }
        }

        return null;
    }


    class MarketOrderMaker implements Runnable {
        private final AtomicBoolean exited;
        private int lastSize;

        public MarketOrderMaker() {
            this.exited = new AtomicBoolean(false);
            this.lastSize = 0;
        }

        @Override
        public void run() {
            while (!exited.get()) {
                try {
                    Thread.sleep(ORDER_MAKER_INTERVAL);

                    // do shrink
                    // 先找一轮能够合并抵消的plan order，放在线程内部操作的目的为了避免加锁
                    if (planOrders.size() > lastSize) {
                        shrink();
                        planOrders.entrySet().removeIf(entry -> entry.getValue().getLeftQuantity() == 0);
                    }

                    for (Map.Entry<Long, PlanOrder> entry : planOrders.entrySet()) {
                        long planOrderId = entry.getKey();
                        PlanOrder planOrder = entry.getValue();

                        List<ExOrder> orderV2s = generateOrders(planOrder);
                        boolean made = false;
                        for (ExOrder order : orderV2s) {
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
                                logger.error("failed to make order ");
                                orderStore.commitExOrder(OrderResultValue.FAIL.name(), order.getId());
                            }
                        }
                        if (!made) {
                            String msg = "failed to make plan order, plan id: " + planOrderId +
                                    ", symbol: " + planOrder.getName() +
                                    ", left size: " + planOrder.getLeftQuantity();
                            logger.error(msg);
                            MonitorReporter.report(String.valueOf(planOrderId), new ReportEvent(
                                    ReportEvent.EventType.HEDGING_MAKE_ORDER_FAILED,
                                    ReportEvent.EventLevel.SERIOUS, msg));
                        }

                    }
                    // 如果已经完成订单，则移除该订单
                    planOrders.entrySet().removeIf(entry -> entry.getValue().getLeftQuantity() == 0);

                    lastSize = planOrders.size();
                } catch (InterruptedException e) {
                    this.exited.set(true);
                    logger.info("market order maker interrupted, existing");
                } catch (Throwable e) {
                    logger.error("failed to run market order maker", e);
                    MonitorReporter.report(ReportEvent.EventType.HEDGING_MARKET_PROCESSOR_FAILED.name(),
                            new ReportEvent(ReportEvent.EventType.HEDGING_MARKET_PROCESSOR_FAILED,
                                    ReportEvent.EventLevel.SERIOUS,
                                    "market order processor failed: " + e.getMessage()));
                }
            }
        }

        public void stop() {
            this.exited.set(true);
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
                                        side1, OrderType.MARKET, 0, 0, oldQty, 0, order1.getId()));

                                OrderSide side2 = order2.getQuantity() > 0 ? OrderSide.BUY : OrderSide.SELL;
                                shrinkOrders.add(new ExOrder(null, order2.getName(), null,
                                        side2, OrderType.MARKET, 0, 0, oldQty, 0, order2.getId()));
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
                        shrinkOrder.getPlanId(), shrinkOrder.getSymbol(),
                        shrinkOrder.getSide(), shrinkOrder.getQuantity());
                // update hedging_plan_order
                PlanOrder planOrder = planOrders.get(shrinkOrder.getPlanId());
                orderStore.updatePlanOrderLeftQuantity(
                        planOrder.getLeftQuantity(), planOrder.getUpdateTime(), planOrder.getId());
            }
        }
    }
}
