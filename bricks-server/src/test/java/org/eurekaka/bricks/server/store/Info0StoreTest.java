package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.model.Info0;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

public class Info0StoreTest extends StoreTestBase {

    @Test
    public void testInfo0Store() throws Exception {
        Info0Store store = new Info0Store();

        Info0 info = new Info0(0, "n1",  "s1", "a1", 1,
                100, 10, false, Map.of("depth", "10"));

        store.store(info);
        Assert.assertEquals(1, info.getId());
        Assert.assertEquals(Collections.singletonList(info), store.query());

        Assert.assertEquals(info, store.queryInfo(1));

        info.setProperty("depth", "40");
        store.update(info);
        Assert.assertEquals(info, store.queryInfo(1));

        store.updateEnabled(1, true);
        info.setEnabled(true);
        Assert.assertEquals(info, store.queryInfo(1));

        store.delete(1);
    }
}
