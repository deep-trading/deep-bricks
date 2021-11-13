package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class StrategyConfigStoreTest extends StoreTestBase {

    @Test
    public void testStrategyConfigStore() throws StoreException {
        StrategyConfigStore store = new StrategyConfigStore();

        StrategyConfig strategyConfig = new StrategyConfig(0, "n1",
                "f1", "i1", false, Map.of("depth", "10"));

        store.store(strategyConfig);
        Assert.assertEquals(1, strategyConfig.getId());
        Assert.assertEquals(Collections.singletonList(strategyConfig), store.query());

        Assert.assertEquals(strategyConfig, store.query(1));

        strategyConfig.setProperty("depth", "40");
        store.update(strategyConfig);
        Assert.assertEquals(strategyConfig, store.query(1));

        store.updateEnabled(1, true);
        strategyConfig.setEnabled(true);
        Assert.assertEquals(strategyConfig, store.query(1));

        store.delete(1);
    }
}
