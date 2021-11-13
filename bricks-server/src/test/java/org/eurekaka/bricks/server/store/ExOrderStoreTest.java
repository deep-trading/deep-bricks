package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.TradeNotification;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderType;
import org.eurekaka.bricks.common.model.PlanOrder;
import org.eurekaka.bricks.server.model.ExOrder;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class ExOrderStoreTest extends StoreTestBase {

    @Test
    public void testExOrderStoreHedgingOrder() throws StoreException {
        ExOrderStore store = new ExOrderStore();
        ExOrder order = new ExOrder("a1", "n1", "s1",
                OrderSide.BUY, OrderType.LIMIT, 1, 2, 2, 2.2, 1);
        store.storeExOrder(order);
        Assert.assertEquals(1, order.getId());

        Assert.assertEquals(Collections.singletonList(order), store.queryUncommittedOrders());
        store.commitExOrder("id1", 1);

        store.storeOrderResult("id1", 1, "FINISHED");
        TradeNotification trade = new TradeNotification("f1", "id1", "a1",
                "n1", "s1", OrderSide.BUY, OrderType.LIMIT, 1, 2, 2,
                "USDT", 1, 1);
        store.storeHistoryOrder(trade);

        List<TradeNotification> trades = store.queryHistoryOrders(null, "n1",
                0, 3, 3);
        Assert.assertEquals(1, trades.size());
        trades.get(0).setId(1);
        Assert.assertEquals(trade, trades.get(0));
    }

    @Test
    public void testExOrderStorePlanOrder() throws StoreException {
        ExOrderStore store = new ExOrderStore();
        PlanOrder planOrder = new PlanOrder(0, "n1", 2,
                4, 2, 1, 1);
        store.storePlanOrder(planOrder);
        Assert.assertEquals(1, planOrder.getId());
        Assert.assertEquals(Collections.singletonList(planOrder), store.queryPlanOrderNotFinished());

        store.updatePlanOrderStartTime(4, 1);
        store.updatePlanOrderLeftQuantity(1, 2, 1);
        planOrder.setLeftQuantity(1);
        planOrder.setStartTime(4);
        planOrder.setUpdateTime(2);
        Assert.assertEquals(Collections.singletonList(planOrder), store.queryPlanOrderNotFinished());

        store.updatePlanOrderLeftQuantity(0, 3, 1);
        Assert.assertEquals(0, store.queryPlanOrderNotFinished().size());
    }
}
