package org.eurekaka.bricks.market.strategy;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.OrderExecutor;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.market.model.StrategyStatus02;
import org.eurekaka.bricks.market.model.StrategyValue02;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Strategy02Test {
    private AccountManager accountManager;
    private OrderExecutor orderExecutor;
    private InfoState<Info0, ?> infoState;
    private StrategyStatus02 strategyStatus;

    @Before
    public void setUp() throws Exception {
        Exchange ex1 = Mockito.mock(Exchange.class);
        List<PositionValue> positionValues = List.of(
                new PositionValue("n1", "s1", "e1",
                        100, 1, 100, 0, 0, 1));
        Mockito.when(ex1.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS))).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, positionValues));

        Exchange ex2 = Mockito.mock(Exchange.class);
        Mockito.when(ex2.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS))).thenReturn(
                new ExMessage(ExMessage.ExMsgType.RIGHT, List.of(
                        new PositionValue("n1", "s2", "e2",
                                -50, 1, -50, 0, 0, 1))));

        accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex1);
        Mockito.when(accountManager.getAccount("e2")).thenReturn(ex2);

        infoState = Mockito.mock(InfoState.class);
        List<Info0> infos = new ArrayList<>();
        infos.add(new Info0(1, "n1", "s1", "e1", 0,
                100, 100, true, Map.of()));
        infos.add(new Info0(2, "n1", "s2", "e2", 2,
                100, 100, true, Map.of()));
        Mockito.when(infoState.getInfos()).thenReturn(infos);

        orderExecutor = Mockito.mock(OrderExecutor.class);

        strategyStatus = Mockito.mock(StrategyStatus02.class);
        Mockito.when(strategyStatus.get("n1")).thenReturn(new StrategyValue02("n1", 50, 1000));
    }

    @Test
    public void testStrategy02() throws Exception {
        StrategyConfig strategyConfig = new StrategyConfig(1,
                "sn1", "c1", "n1", true, Map.of(
                        "strategy_interval", "1",
                "strategy_base_interval", "3600000",
                "target_accounts", "e1"));
        Strategy02 strategy = new Strategy02(strategyConfig,
                accountManager, infoState, orderExecutor, strategyStatus);

        strategy.start();

        Thread.sleep(100);

        strategy.run();
        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(-50L), Mockito.eq(100000000L));
        ArgumentCaptor<StrategyValue02> arg1 = ArgumentCaptor.forClass(StrategyValue02.class);
        Mockito.verify(strategyStatus).put(arg1.capture());
        Assert.assertEquals(100, arg1.getValue().value, 0.0001);
        Assert.assertEquals("n1", arg1.getValue().name);
        strategy.stop();
    }


    @Test
    public void testStrategy02HedgingBase() throws Exception {
        StrategyConfig strategyConfig = new StrategyConfig(1,
                "sn1", "c1", "n1", true, Map.of(
                "strategy_interval", "3600000",
                "strategy_base_interval", "1",
                "target_accounts", "e1"));
        Strategy02 strategy = new Strategy02(strategyConfig,
                accountManager, infoState, orderExecutor, strategyStatus);
        strategy.start();

        Thread.sleep(100);

        strategy.run();
        Mockito.verify(orderExecutor).makeOrder(Mockito.eq("n1"),
                Mockito.eq(-50L), Mockito.eq(100000000L));
        ArgumentCaptor<StrategyValue02> arg1 = ArgumentCaptor.forClass(StrategyValue02.class);
        Mockito.verify(strategyStatus).put(arg1.capture());
        Assert.assertEquals(100, arg1.getValue().value, 0.0001);
        Assert.assertEquals("n1", arg1.getValue().name);

        strategy.stop();
    }
}
