package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.market.strategy.Strategy01;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.*;

public class Strategy01Test {
    private Info0 strategyInfo;
    private StrategyConfig strategyConfig;
    private InfoState<Info0, ?> infoState;
    private AccountManager accountManager;
    private Exchange e1;

    @Before
    public void setUp() throws Exception {
        List<Info0> infos = new ArrayList<>();
        infos.add(new Info0(1, "n1", "s1", "e1", 0,
                1000, 100, true,
                Map.of("side", "ALL")));
        infos.add(new Info0(2, "n1", "s2", "e2", 2,
                100, 100, true,
                Map.of("depth_qty", "100", "side", "ALL")));

        strategyConfig = new StrategyConfig(1, "sn1", "c1", "n1", true,
                Map.of( "target_account", "e1",
                        "total_order_count", "3",
                        "cancel_count", "2",
                        "min_price_rate", "0.002",
                        "max_price_rate", "0.2",
                        "debug", "true"));

        infoState = Mockito.mock(InfoState.class);
        Mockito.when(infoState.getInfoByName("n1")).thenReturn(infos);

        accountManager = Mockito.mock(AccountManager.class);

        e1 = Mockito.mock(Exchange.class);
        Mockito.when(e1.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair("n1", "s1", 0))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, Collections.EMPTY_LIST));
        Mockito.when(e1.getTakerRate()).thenReturn(0.001);
        Mockito.when(e1.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.001));
        Mockito.when(accountManager.getAccount("e1")).thenReturn(e1);

        Exchange e2 = Mockito.mock(Exchange.class);
        Mockito.when(e2.getTakerRate()).thenReturn(0.001);
        Mockito.when(e2.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 0.99, 233, 235.35)));
        Mockito.when(e2.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s2", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s2", 1.02, 413, 404.9)));
        Mockito.when(e2.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                new SymbolPair("n1", "s2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 0.001));
        Mockito.when(e2.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT)))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, 1.0));
        Mockito.when(accountManager.getAccount("e2")).thenReturn(e2);
    }

    @Test
    public void testRunOrders() throws Exception {
        Strategy01 strategy = new Strategy01(null, strategyConfig);

        strategy.start();
        // 第一次run，生成3个买订单，3个卖订单
        strategy.run();
        // 第二次run，撤销两个订单，生成两个订单
        strategy.run();

        strategy.stop();
    }

    @Test
    public void testNotifyOrder() throws Exception {
        List<CurrentOrder> currentOrders = new ArrayList<>();
        currentOrders.add(new CurrentOrder("id1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 100, 1, 10));
        currentOrders.add(new CurrentOrder("id2", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 80, 0.9, 0));
        currentOrders.add(new CurrentOrder("id3", "n1", "s1",
                OrderSide.SELL, OrderType.LIMIT, 30, 1.1, 0));
        currentOrders.add(new CurrentOrder("id4", "n1", "s1",
                OrderSide.SELL, OrderType.LIMIT, 110, 1.1, 0));

        Mockito.when(e1.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair("n1", "s1", 0))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, currentOrders));

        Strategy01 strategy = new Strategy01(null, strategyConfig);
        strategy.start();

        strategy.notify(new OrderNotification("id1", "n1", "s1", "e1",
                OrderSide.BUY, OrderType.LIMIT, 100, 1, 100));
        strategy.notify(new OrderNotification("id3", "n1", "s1", "e1",
                OrderSide.SELL, OrderType.LIMIT, 30, 1.1, 30));

        strategy.stop();
    }
}
