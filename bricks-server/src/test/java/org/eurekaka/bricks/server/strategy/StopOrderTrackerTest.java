package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.AccountActor;
import org.eurekaka.bricks.api.OrderTracker;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class StopOrderTrackerTest {

    @Test
    public void testStopOrderTrackerExpired() throws Exception {
        StrategyConfig strategyConfig = new StrategyConfig();
        strategyConfig.setProperties(Map.of(
                "order_alive_time", "100",
                "min_order_quantity", "3"));
        AccountActor accountActor = Mockito.mock(AccountActor.class);

        OrderTracker tracker = new StopOrderTracker(strategyConfig, accountActor);
        tracker.init(Collections.emptyList());

        long orderTime1 = System.currentTimeMillis();
        Order order1 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT_GTC, 10, 1, 10, "cid1");
        CurrentOrder currentOrder1 = new CurrentOrder("id1", "n1", "s1" ,"e1",
                OrderSide.BUY, OrderType.LIMIT_GTC, 10, 1, 0,
                OrderStatus.NEW, orderTime1, "cid1");

        Mockito.when(accountActor.asyncMakeOrder(order1))
                .thenReturn(CompletableFuture.completedFuture(currentOrder1));
        Mockito.when(accountActor.getAskDepthPrice("e1", "n1", "s1", 10))
                .thenReturn(new DepthPrice("n1", "s1", 1.01, 100, 100));
        Mockito.when(accountActor.asyncGetOrder("e1", "n1", "s1", "cid1"))
                .thenReturn(CompletableFuture.completedFuture(currentOrder1));
        Mockito.when(accountActor.asyncCancelOrder("e1", "n1", "s1", "cid1"))
                .thenReturn(CompletableFuture.completedFuture(null));

        tracker.submit(order1);

        Thread.sleep(150);
        tracker.track();

        Thread.sleep(100);

        Order order2 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.MARKET, 10, 1, 10, "cid1_1");
        ArgumentCaptor<Order> arg1 = ArgumentCaptor.forClass(Order.class);
        Mockito.verify(accountActor, Mockito.times(2)).asyncMakeOrder(arg1.capture());
        Assert.assertEquals(order2, arg1.getValue());
    }

    @Test
    public void testStopOrderTrackerRiskPrice() throws Exception {
        StrategyConfig strategyConfig = new StrategyConfig();
        strategyConfig.setProperties(Map.of(
                "order_risk_rate", "0.001",
                "min_order_quantity", "3"));
        AccountActor accountActor = Mockito.mock(AccountActor.class);

        OrderTracker tracker = new StopOrderTracker(strategyConfig, accountActor);
        tracker.init(Collections.emptyList());


        Order order1 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT_GTC, 10, 1, 10, "cid1");
        CurrentOrder currentOrder1 = new CurrentOrder("id1", "n1", "s1" ,"e1",
                OrderSide.BUY, OrderType.LIMIT_GTC, 10, 1, 0,
                OrderStatus.NEW, 0, "cid1");

        Mockito.when(accountActor.asyncMakeOrder(order1))
                .thenReturn(CompletableFuture.completedFuture(currentOrder1));
        Mockito.when(accountActor.getAskDepthPrice("e1", "n1", "s1", 10))
                .thenReturn(new DepthPrice("n1", "s1", 1.0005, 100, 100));

        tracker.submit(order1);

        Thread.sleep(100);
        tracker.track();

        Mockito.when(accountActor.getAskDepthPrice("e1", "n1", "s1", 10))
                .thenReturn(new DepthPrice("n1", "s1", 1.002, 100, 100));

        Mockito.when(accountActor.asyncCancelOrder("e1", "n1", "s1", "cid1"))
                .thenReturn(CompletableFuture.completedFuture(null));
        CurrentOrder currentOrder2 = new CurrentOrder("id1", "n1", "s1" ,"e1",
                OrderSide.BUY, OrderType.LIMIT_GTC, 10, 1, 3,
                OrderStatus.PART_FILLED, 0, "cid1");
        Mockito.when(accountActor.asyncGetOrder("e1", "n1", "s1", "cid1"))
                .thenReturn(CompletableFuture.completedFuture(currentOrder2));

        tracker.track();
        Thread.sleep(100);

        Order order2 = new Order("e1", "n1", "s1",
                OrderSide.BUY, OrderType.MARKET, 7, 1, 7, "cid1_1");
        ArgumentCaptor<Order> arg1 = ArgumentCaptor.forClass(Order.class);
        Mockito.verify(accountActor, Mockito.times(2)).asyncMakeOrder(arg1.capture());
        Assert.assertEquals(order2, arg1.getValue());
    }
}
