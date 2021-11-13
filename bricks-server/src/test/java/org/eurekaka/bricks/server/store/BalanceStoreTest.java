package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountBalance;
import org.eurekaka.bricks.common.model.AccountProfit;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class BalanceStoreTest extends StoreTestBase {

    @Test
    public void testBalanceStore() throws StoreException {
        BalanceStore store = new BalanceStore();
        AccountProfit balance = new AccountProfit("a1", "e1", 15,
                10, 1.5, 15);
        balance.setTime(300000);
        store.storeAccountProfit(balance);
        Assert.assertEquals(Collections.singletonList(balance),
                store.queryAccountProfit(300000));
    }
}
