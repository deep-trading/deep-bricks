package org.eurekaka.bricks.server.executor;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.model.ExOrder;
import org.eurekaka.bricks.server.store.ExOrderStore;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FutureOrderExecutorV1Test {
    private final String name = "n1";
    private final String symbol = "s1";

    private AccountManager accountManager;
    private InfoState<Info0, ?> infoState;
    private ExOrderStore store;

    @Before
    public void setUp() throws Exception {
        accountManager = Mockito.mock(AccountManager.class);

        infoState = Mockito.mock(InfoState.class);

        store = Mockito.mock(ExOrderStore.class);
    }

    @Test
    public void testLimitOrderUpdateCurrentOrder() throws Exception {
        Exchange ex1 = Mockito.mock(Exchange.class);
        Mockito.when(ex1.isAlive()).thenReturn(true);
        Mockito.when(ex1.getName()).thenReturn("e1");
        Mockito.when(ex1.getTakerRate()).thenReturn(0.0005);
        Mockito.when(ex1.getMakerRate()).thenReturn(-0.0002);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE, new SymbolPair(name, symbol))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.001));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 1.0));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 5.125, 325, 63.4)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 5.112, 455, 63.4)));
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        Mockito.when(infoState.getInfoByName(name)).thenReturn(List.of(
                new Info0(1, name, symbol, "e1", 1, 1000,
                        100, true, Map.of("side", "ALL"))));

        OrderExecutor orderExecutor = new FutureOrderExecutorV1(
                Map.of("add_one", "true"), accountManager, infoState, store);
        orderExecutor.start();

        orderExecutor.makeOrder(new PlanOrder(1, name, -900, 511800000,
                900, 0, 0));
        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.SELL, OrderType.LIMIT,
                175.85, 5.124, 900, 5.131, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        Thread.sleep(700);
        // 第一次循环

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 5.123, 325, 63.4)));
        CurrentOrder currentOrder1 = new CurrentOrder("id1", symbol,
                OrderSide.SELL, OrderType.LIMIT, 175.85, 5.124, 55.35);
        currentOrder1.setName(name);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair(name, symbol, "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, currentOrder1));
        ExOrder order2 = new ExOrder("e1", name, symbol, OrderSide.SELL, OrderType.LIMIT,
                120.55, 5.122, 617, 5.129, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order2)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));

        Thread.sleep(500);
        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(617L), Mockito.anyLong(), Mockito.eq(1L));

        // 开始退场
        CurrentOrder currentOrder2 = new CurrentOrder(
                "id2", symbol, OrderSide.SELL, OrderType.LIMIT, 120.55, 5.124, 94.72);
        currentOrder2.setName(name);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair(name, symbol, "id2")))).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, currentOrder2));

        orderExecutor.stop();

        Thread.sleep(1000);

        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(132L), Mockito.anyLong(), Mockito.eq(1L));
    }

    @Test
    public void testLimitOrderNotify() throws Exception {
        Exchange ex1 = Mockito.mock(Exchange.class);
        Mockito.when(ex1.isAlive()).thenReturn(true);
        Mockito.when(ex1.getName()).thenReturn("e1");
        Mockito.when(ex1.getTakerRate()).thenReturn(0D);
        Mockito.when(ex1.getMakerRate()).thenReturn(0D);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE, new SymbolPair(name, symbol))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0D));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 1.0));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, new DepthPrice(
                        name, symbol, 1.1, 1100, 1000)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, new DepthPrice(
                        name, symbol, 0.9, 1800, 2000)));
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        Mockito.when(infoState.getInfoByName(name)).thenReturn(List.of(
                new Info0(1, name, symbol, "e1", 1, 100,
                        100, true, Map.of(
                                "side", "ALL"))));

        OrderExecutor orderExecutor = new FutureOrderExecutorV1(Map.of("add_one", "true"),
                accountManager, infoState, store);
        orderExecutor.start();

        PlanOrder planOrder = new PlanOrder(1, name, 1000L,
                100000000L, 1000L, 1L, 1);
        orderExecutor.makeOrder(planOrder);

        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.LIMIT,
                1000, 0.91, 1000, 0.91, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        // 保证已经生成挂单， 0.91, 1000 的买单
        Thread.sleep(700);

        orderExecutor.notify(new OrderNotification("id1", name, symbol,
                "e1", OrderSide.BUY, OrderType.LIMIT, 1000, 0.91, 1000));
        orderExecutor.notify(new OrderNotification("id0", name, symbol, "e1",
                OrderSide.BUY, OrderType.LIMIT, 600, 0.91, 300));

        Thread.sleep(500);

        Mockito.verify(store).storeOrderResult(Mockito.eq("id1"),
                Mockito.eq(0D), Mockito.eq("FILLED"));
        Mockito.verify(store).updatePlanOrderLeftQuantity(Mockito.eq(0L),
                Mockito.anyLong(), Mockito.eq(1L));

        Mockito.verify(store).updatePlanOrderLeftQuantity(Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(1L));

        orderExecutor.stop();
    }

    @Test
    public void testLimitOrderExpired() throws Exception {
        Mockito.when(infoState.getInfoByName(name)).thenReturn(Collections.singletonList(
                new Info0(1, name, symbol, "e1", 1, 1000,
                        100, true, Map.of(
                                "side", "ALL",
                        "depth", "100"))));

        Exchange ex1 = Mockito.mock(Exchange.class);
        Mockito.when(ex1.isAlive()).thenReturn(true);
        Mockito.when(ex1.getName()).thenReturn("e1");
        Mockito.when(ex1.getTakerRate()).thenReturn(0.0005);
        Mockito.when(ex1.getMakerRate()).thenReturn(-0.0002);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE, new SymbolPair(name, symbol))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.001));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 1.0));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, new DepthPrice(
                        name, symbol, 5.125, 325, 63.4)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol, DepthPricePair.ZERO_DEPTH_QTY))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, new DepthPrice(
                        name, symbol, 5.112, 455, 63.4)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, new DepthPrice(
                        name, symbol, 5.126, 197, 38.5)));

        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.LIMIT,
                156.31, 5.113, 800, 5.117, 1);

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));
        CurrentOrder currentOrder1 = new CurrentOrder("id1", symbol,
                OrderSide.BUY, OrderType.LIMIT, 156.31, 5.113, 35.81);
        currentOrder1.setName(name);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair(name, symbol, "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, currentOrder1));

        ExOrder order2 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.MARKET,
                34.58, 5.126, 177, 5.134, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order2)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));

        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        OrderExecutor orderExecutor = new FutureOrderExecutorV1(Map.of(
                "order_interval", "600",
                "order_expired_time", "1000",
                "add_one", "true"), accountManager, infoState, store);
        orderExecutor.start();

        PlanOrder planOrder1 = new PlanOrder(1, name, 800, 511800000, 800,
                System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder1);

        Thread.sleep(2000);
        // 下过一个market order

        // 下一个limit order，left quantity = 617
        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(617L), Mockito.anyLong(), Mockito.eq(1L));

        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(440L), Mockito.anyLong(), Mockito.eq(1L));

        orderExecutor.stop();
    }
}
