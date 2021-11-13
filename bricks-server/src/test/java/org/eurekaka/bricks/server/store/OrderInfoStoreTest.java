package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.server.model.OrderInfo;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class OrderInfoStoreTest extends StoreTestBase {

    @Test
    public void testOrderSymInfoStore() throws StoreException {
        OrderInfoStore store = new OrderInfoStore();

        OrderInfo info = new OrderInfo(0, "n1", "s1", "a1",
                100, 10, 20, OrderSide.BUY, false);
        store.store(info);
        Assert.assertEquals(1, info.getId());
        Assert.assertEquals(Collections.singletonList(info), store.query());

        Assert.assertEquals(info, store.queryInfo(1));

        info.setDepthQty(40);
        store.update(info);
        Assert.assertEquals(info, store.queryInfo(1));

        store.updateEnabled(1, true);
        info.setEnabled(true);
        Assert.assertEquals(info, store.queryInfo(1));

        store.delete(1);
    }
}
