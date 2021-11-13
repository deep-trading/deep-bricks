package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

public class Strategy03Test {

    private AccountManager accountManager;
    private InfoState<Info0, ?> infoState;
    private StrategyConfig strategyConfig;
    private Strategy strategy;
    private OrderExecutor orderExecutor;

    private Exchange ex1;
    private Exchange ex2;

    @Before
    public void setUp() throws Exception {
        this.accountManager = Mockito.mock(AccountManager.class);
        this.infoState = Mockito.mock(InfoState.class);
        this.orderExecutor = Mockito.mock(OrderExecutor.class);

        strategyConfig = new StrategyConfig(1, "s1", "c1",
                "n1", true, Map.of(
                        "is_debug", "true",
                "price_rate", "0.001"));

        Mockito.when(infoState.getInfoByName("n1"))
                .thenReturn(List.of(
                        new Info0(1, "n1", "s1", "e1",
                                1, 1000, 100, true,
                                Map.of("depth_qty", "100")),
                        new Info0(2, "n1", "s2", "e2",
                                1, 1000, 100, true,
                                Map.of())));

        ex1 = Mockito.mock(Exchange.class);
        ex2 = Mockito.mock(Exchange.class);

        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);
        Mockito.when(accountManager.getAccount("e2")).thenReturn(ex2);

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair("n1", "s1", 0))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, List.of()));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair("n1", "s2", 0))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, List.of()));

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.0001));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair("n1", "s2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.0001));

        Mockito.when(ex1.getTakerRate()).thenReturn(0.001);
        Mockito.when(ex2.getTakerRate()).thenReturn(0.001);

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.151, 379, 329.28)));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.159, 217, 187.23)));

        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 79))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.149, 779, 677.98)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 79))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.156, 457, 395.33)));
    }

    @After
    public void tearDown() throws Exception {
        strategy.stop();
    }

    @Test
    public void testRunUpdateOrderWithPriceRate() throws Exception {
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                106.68, 1.153, 123, 0, 0, 1)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s2", "e2",
                                -115.65, 1.15, -133, 0, 0, 1)));

        strategy = new Strategy03(strategyConfig, accountManager, infoState, orderExecutor);
        strategy.start();

        // 应当生成的订单
        Order order1 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 87.34, 1.145, 100);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order1)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));
        Order order2 = new Order("e1", "n1", "s1",
                OrderSide.SELL, OrderType.LIMIT, 106.03, 1.16, 123);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order2)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));

        Order order3 = new Order("e2", "n1", "s2",
                OrderSide.BUY, OrderType.LIMIT, 115.95, 1.147, 133);
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order3)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id3"));
        Order order4 = new Order("e2", "n1", "s2",
                OrderSide.SELL, OrderType.LIMIT, 85.98, 1.163, 100);
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order4)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id4"));
        // 第一次run，产生四个挂单
        strategy.run();

        // 第二次run，更改买一卖一价格
        // 触发price_rate
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 79))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.152, 577, 500.87)));
        // s2买一价升高，s1挂单买家距离good price的百分比 > price rate
        Order order5 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 87.11, 1.148, 100);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order5)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id5"));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id1", "n1", "s1",
                                OrderSide.BUY, OrderType.LIMIT, 87.34, 1.145, 0)));
        strategy.run();

        // 第三次run，触发profit rate
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 79))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.158, 371, 320.38)));
        Order order6 = new Order("e1", "n1", "s1",
                OrderSide.SELL, OrderType.LIMIT, 105.85, 1.162, 123);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order6)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id6"));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id2", "n1", "s1",
                                OrderSide.BUY, OrderType.LIMIT, 106.03, 1.16, 83.6)));
        strategy.run();
        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(-97L), Mockito.eq(116028708L));

        TradeNotification trade = new TradeNotification("fid1", "id5",
                "e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                1.148, 87, 99.876, "U", 0.3, 1L);
        strategy.notify(trade);
        // 触发最小数量撤单，挂单
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 79))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.153, 386, 334.78)));
        Order order7 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 87.03, 1.149, 100);
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order7)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id7"));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id5"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id5", "n1", "s1",
                                OrderSide.BUY, OrderType.LIMIT, 0.11, 1.148, 87)));
        strategy.run();
        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(-100L), Mockito.eq(114942529L));
    }

    @Test
    public void testRunClosePositionMode() throws Exception {
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                973.98, 1.153, 1123, 0, 0, 1)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s2", "e2",
                                -985.22, 1.15, -1133, 0, 0, 1)));

        strategy = new Strategy03(strategyConfig, accountManager, infoState, orderExecutor);
        strategy.start();

        // 触发 currentState = 2
        strategy.run();

        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(-1123L), Mockito.eq(115300000L));
        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(1133L), Mockito.eq(115000000L));

        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                1, 1.153, 1, 0, 0, 1)));
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s2", "e2",
                                -3, 1.15, -3, 0, 0, 1)));

        strategy.run();
    }
}
