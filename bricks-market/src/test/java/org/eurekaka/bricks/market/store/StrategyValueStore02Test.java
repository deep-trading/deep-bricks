package org.eurekaka.bricks.market.store;

import org.eurekaka.bricks.market.model.StrategyValue02;
import org.eurekaka.bricks.server.store.StoreTestBase;
import org.junit.Assert;
import org.junit.Test;

public class StrategyValueStore02Test extends StoreTestBase {

    @Test
    public void testStrategyValueStore02() throws Exception {
        StrategyValueStore02 store = new StrategyValueStore02();
        StrategyValue02 value = new StrategyValue02("n1", 1, 2);
        store.store(value);

        Assert.assertEquals(value, store.query("n1"));
    }
}
