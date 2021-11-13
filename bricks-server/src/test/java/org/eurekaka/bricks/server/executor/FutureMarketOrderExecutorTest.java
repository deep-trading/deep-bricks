package org.eurekaka.bricks.server.executor;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FutureMarketOrderExecutorTest {
    @Test
    public void testMarketOrderExecutorShrink() throws Exception {
        AccountManager accountManager = Mockito.mock(AccountManager.class);
        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        ExOrderStore store = Mockito.mock(ExOrderStore.class);

        OrderExecutor orderExecutor = new FutureMarketOrderExecutor(accountManager, infoState, store);

        PlanOrder planOrder1 = new PlanOrder(1, "n1", 1200,
                10, 1200, 1000, 1000);
        PlanOrder planOrder2 = new PlanOrder(2, "n1", -800,
                10, 800, 1000, 1000);
        orderExecutor.makeOrder(planOrder1);
        orderExecutor.makeOrder(planOrder2);

        orderExecutor.start();

        Thread.sleep(600);

        // 此时应当完成一次shrink，以及存储订单
        ExOrder order1 = new ExOrder(null, "n1", null,
                OrderSide.BUY, OrderType.MARKET, 0, 0, 800, 0, 1);
        ExOrder order2 = new ExOrder(null, "n1", null,
                OrderSide.SELL, OrderType.MARKET, 0, 0, 800, 0, 2);

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
    public void testMarketOrderExecutorMakeOrder() throws Exception {
        String name = "n1";
        String symbol = "s1";
        long symbolPrice = 819500000;
        SymbolPair symbolPair = new SymbolPair(name, symbol);

        ExAction<?> action1 = new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE, symbolPair);
        ExAction<?> action2 = new ExAction<>(ExAction.ActionType.GET_MARK_USDT);

        Exchange ex1 = Mockito.mock(Exchange.class);
        Mockito.when(ex1.isAlive()).thenReturn(true);
        Mockito.when(ex1.getName()).thenReturn("e1");
        Mockito.when(ex1.getTakerRate()).thenReturn(0.001);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol,400))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.125, 425, 52.31)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 400))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.195, 555, 67.8)));
        Mockito.when(ex1.process(action1)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 0.00025));
        Mockito.when(ex1.process(action2)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 1D));

        Exchange ex2 = Mockito.mock(Exchange.class);
        Mockito.when(ex2.isAlive()).thenReturn(true);
        Mockito.when(ex2.getName()).thenReturn("ex2");
        Mockito.when(ex2.getTakerRate()).thenReturn(0.0005);
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol,800))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.325, 925, 111.11)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 800))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.355, 1055, 126.27)));
        Mockito.when(ex2.process(action1)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 0.0002));
        Mockito.when(ex2.process(action2)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 1D));

        Exchange ex3 = Mockito.mock(Exchange.class);
        Mockito.when(ex3.isAlive()).thenReturn(true);
        Mockito.when(ex3.getName()).thenReturn("ex3");
        Mockito.when(ex3.getTakerRate()).thenReturn(0.00075);
        Mockito.when(ex3.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol,600))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 7.988, 824, 103.15)));
        Mockito.when(ex3.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 600))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.025, 680, 84.73)));
        Mockito.when(ex3.process(action1)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 0.0001));
        Mockito.when(ex3.process(action2)).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, 1D));

        AccountManager accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);
        Mockito.when(accountManager.getAccount("e2")).thenReturn(ex2);
        Mockito.when(accountManager.getAccount("e3")).thenReturn(ex3);

        InfoState<Info0, ?> infoState = Mockito.mock(InfoState.class);
        List<Info0> infos = new ArrayList<>();
        infos.add(new Info0(1, name, symbol, "e1", 1,
                1000, 100, true,
                Map.of("side", "ALL", "depth", "400")));
        infos.add(new Info0(2, name, symbol, "e2", 1,
                1000, 100, true,
                Map.of("side", "BUY", "depth", "800")));
        infos.add(new Info0(3, name, symbol, "e3", 1,
                1000, 100, true,
                Map.of("side", "SELL", "depth", "600")));
        Mockito.when(infoState.getInfoByName(name)).thenReturn(infos);

        ExOrderStore orderStore = Mockito.mock(ExOrderStore.class);
        OrderExecutor orderExecutor = new FutureMarketOrderExecutor(accountManager, infoState, orderStore);
        orderExecutor.start();

        PlanOrder planOrder1 = new PlanOrder(1, name, 870, symbolPrice,
                870, System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder1);

        // 在hedger1 下单 555 ，因为hedger3只接受sell
        ExOrder order1 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.MARKET,
                65.28, 8.195, 535, 8.206, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        Thread.sleep(550);

        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id1"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(335L), Mockito.anyLong(), Mockito.eq(1L));
        System.out.println(planOrder1);


        ExOrder order2 = new ExOrder("e1", name, symbol, OrderSide.BUY, OrderType.MARKET,
                40.88, 8.195, 335, 8.206, 1);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order2)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));
        Thread.sleep(500);
        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id2"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(1L));
        System.out.println(planOrder1);


        PlanOrder planOrder2 = new PlanOrder(2, name, -670, symbolPrice,
                670, System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder2);

        ExOrder order3 = new ExOrder("e1", name, symbol, OrderSide.SELL, OrderType.MARKET,
                49.42, 8.125, 405, 8.118, 2);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order3)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id3"));

        Thread.sleep(550);

        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id3"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(265L), Mockito.anyLong(), Mockito.eq(2L));
        System.out.println(planOrder2);

        ExOrder order4 = new ExOrder("e1", name, symbol, OrderSide.SELL, OrderType.MARKET,
                32.34, 8.125, 265, 8.118, 2);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order4)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id4"));

        Thread.sleep(500);

        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id4"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(2L));
        System.out.println(planOrder2);

        // 调整 ex2，与 ex1 比价，此时订单转入 ex2 下单
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol,800))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.075, 925, 114.55)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 800))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.1, 1055, 130.25)));
        PlanOrder planOrder3 = new PlanOrder(3, name, 660, symbolPrice,
                660, System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder3);

        ExOrder order5 = new ExOrder("e2", name, symbol, OrderSide.BUY, OrderType.MARKET,
                80.54, 8.1, 660, 8.106, 3);
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order5)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id5"));

        Thread.sleep(500);
        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id5"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(3L));
        System.out.println(planOrder3);

        // 调整 ex3，与 ex1比价，此时订单转入 ex3下单
        Mockito.when(ex3.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair(name, symbol,600))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.275, 725, 87.61)));
        Mockito.when(ex3.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair(name, symbol, 600))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice(name, symbol, 8.3, 1055, 127.11)));
        PlanOrder planOrder4 = new PlanOrder(4, name, -230, symbolPrice,
                230, System.currentTimeMillis(), System.currentTimeMillis());
        orderExecutor.makeOrder(planOrder4);

        ExOrder order6 = new ExOrder("e3", name, symbol, OrderSide.SELL, OrderType.MARKET,
                28.07, 8.275, 230, 8.269, 4);
        Mockito.when(ex3.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order6)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id6"));

        Thread.sleep(500);
        Mockito.verify(orderStore).commitExOrder(Mockito.eq("id6"), Mockito.eq(0L));
        Mockito.verify(orderStore).updatePlanOrderLeftQuantity(
                Mockito.eq(0L), Mockito.anyLong(), Mockito.eq(4L));
        System.out.println(planOrder4);

        orderExecutor.stop();
    }
}
