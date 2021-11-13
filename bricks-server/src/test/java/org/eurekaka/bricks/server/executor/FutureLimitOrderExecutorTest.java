package org.eurekaka.bricks.server.executor;

import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.model.ExOrder;
import org.eurekaka.bricks.server.store.ExOrderStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FutureLimitOrderExecutorTest {

    @Test
    public void testFutureLimitOrderExecutorShrink() throws Exception {
        AccountManager accountManager = Mockito.mock(AccountManager.class);
        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        ExOrderStore store = Mockito.mock(ExOrderStore.class);

        OrderExecutor orderExecutor = new FutureLimitOrderExecutor(Map.of(),
                accountManager, infoState, null, store);

        orderExecutor.start();

        PlanOrder planOrder1 = new PlanOrder(1, "n1", 1200,
                10, 1200, 1000, 1000);
        PlanOrder planOrder2 = new PlanOrder(2, "n1", -800,
                10, 800, 1000, 1000);
        orderExecutor.makeOrder(planOrder1);
        orderExecutor.makeOrder(planOrder2);

        Thread.sleep(600);

        // 此时应当完成一次shrink，以及存储订单
        ExOrder order1 = new ExOrder(null, "n1", null,
                OrderSide.BUY, OrderType.LIMIT, 0, 0, 800, 0, 1);
        ExOrder order2 = new ExOrder(null, "n1", null,
                OrderSide.SELL, OrderType.LIMIT, 0, 0, 800, 0, 2);

        ArgumentCaptor<ExOrder> arg2 = ArgumentCaptor.forClass(ExOrder.class);
        Mockito.verify(store, Mockito.times(2)).storeExOrder(arg2.capture());
        Assert.assertEquals(order1, arg2.getAllValues().get(0));
        Assert.assertEquals(order2, arg2.getAllValues().get(1));
        System.out.println(arg2.getAllValues());

        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(400L), Mockito.anyLong(), Mockito.eq(1L));
        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(2L));

        orderExecutor.stop();
    }

    @Test
    public void testFutureLimitOrderExecutorExpiredOrder() throws Exception {
        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        ExOrderStore store = Mockito.mock(ExOrderStore.class);
        OrderExecutor marketOrderExecutor = Mockito.mock(OrderExecutor.class);

        String name = "n1";
        String symbol = "s1";
        Mockito.when(infoState.getInfoByName(name)).thenReturn(Collections.singletonList(
                new Info0(1, name, symbol, "e1", 1, 1000,
                100, true, Map.of("side", "ALL"))));

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

        AccountManager accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        Map<String, Object> configMap = new HashMap<>();
        configMap.put("limit_check_interval", 600);
        configMap.put("limit_expired_time", 1000);

        OrderExecutor orderExecutor = new FutureLimitOrderExecutor(Map.of(
                "limit_check_interval", "600",
                "limit_expired_time", "1000"),
                accountManager, infoState, marketOrderExecutor, store);

        orderExecutor.start();

        PlanOrder planOrder1 = new PlanOrder(1, name, 800, 511800000, 800,
                System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder1);

        Thread.sleep(2000);

        // marketOrderExecutor 收到 plan order
        ArgumentCaptor<PlanOrder> arg1 = ArgumentCaptor.forClass(PlanOrder.class);
        Mockito.verify(marketOrderExecutor).makeOrder(arg1.capture());
        Assert.assertEquals(617L, arg1.getValue().getLeftQuantity());
        Assert.assertEquals(800L, arg1.getValue().getQuantity());
        Assert.assertEquals(name, arg1.getValue().getName());

        // plan order update left quantity 1 次
        Mockito.verify(store).updatePlanOrderLeftQuantity(
                Mockito.eq(617L), Mockito.anyLong(), Mockito.eq(1L));
    }

    @Test
    public void testFutureLimitOrderExecutorUpdateCurrentOrder() throws Exception {
        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        ExOrderStore store = Mockito.mock(ExOrderStore.class);
        OrderExecutor marketOrderExecutor = Mockito.mock(OrderExecutor.class);

        String name = "n1";
        String symbol = "s1";
        Mockito.when(infoState.getInfoByName(name)).thenReturn(Collections.singletonList(
                new Info0(1, name, symbol, "e1", 1, 1000,
                        100, true, Map.of("side", "ALL"))));

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

        AccountManager accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        OrderExecutor orderExecutor = new FutureLimitOrderExecutor(Map.of(),
                accountManager, infoState, marketOrderExecutor, store);

        orderExecutor.start();

        orderExecutor.makeOrder(new PlanOrder(1, name, -900, 511800000,
                900, System.currentTimeMillis(), System.currentTimeMillis()));

        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.SELL, OrderType.LIMIT,
                175.85, 5.124, 900, 5.131, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        Thread.sleep(1100);

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
    public void testLimitOrderExecutorNotifyCurrentOrder() throws Exception {
        String name = "n1";
        String symbol = "s1";

        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        Info0 info = new Info0(1, name, symbol, "e1", 1,
                100, 100, true,
                Map.of("depth", "300", "side", "ALL"));
        Mockito.when(infoState.getInfoByName("n1")).thenReturn(Collections.singletonList(info));

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


        AccountManager accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);

        ExOrderStore store = Mockito.mock(ExOrderStore.class);
        Mockito.when(store.queryPlanOrderNotFinished()).thenReturn(Collections.emptyList());

        OrderExecutor orderExecutor = new FutureLimitOrderExecutor(Map.of(),
                accountManager, infoState, null, store);
        orderExecutor.start();

        PlanOrder planOrder = new PlanOrder(1, "n1", 1000L,
                100000000L, 1000L, 1L, 1);
        orderExecutor.makeOrder(planOrder);

        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.LIMIT,
                1000, 0.91, 1000, 0.91, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        // 保证已经生成挂单， 0.91, 1000 的买单
        Thread.sleep(600);

        OrderNotification currentOrder = new OrderNotification("id1", name, "e1",
                symbol, OrderSide.BUY, OrderType.LIMIT, 1000, 0.91, 1000);

        orderExecutor.notify(currentOrder);

        orderExecutor.notify(new OrderNotification("id0", name, symbol, "e1",
                OrderSide.BUY, OrderType.LIMIT, 600, 0.91, 300));

        Thread.sleep(500);

        Mockito.verify(store).storeOrderResult(Mockito.eq("id1"),
                Mockito.eq(0D), Mockito.eq("FILLED"));
        Mockito.verify(store).updatePlanOrderLeftQuantity(Mockito.eq(0L),
                Mockito.anyLong(), Mockito.eq(1L));

        Mockito.verify(store).updatePlanOrderLeftQuantity(Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(1L));
    }
}
