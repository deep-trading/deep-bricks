package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountBalance;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class AssetBaseValueStoreTest extends StoreTestBase {

//    @Test
//    public void testAssetBaseValueStoreAccountBalance() throws StoreException {
//        AssetBaseValueStore store = new AssetBaseValueStore();
//
//        AccountBalance accountBalance = new AccountBalance(
//                "a1", "a2", 1, 2, 2, 60000);
//        store.storeAccountBalance(accountBalance);
//
//        Assert.assertEquals(Collections.singletonList(accountBalance),
//                store.queryAccountBalance(60000));
//    }

    @Test
    public void testAssetBaseValueStoreAssetBaseValue() throws StoreException {
        AssetBaseValueStore store = new AssetBaseValueStore();

        AssetBaseValue assetBaseValue = new AssetBaseValue(
                0, "a1", "a2", 10, false);
        store.storeAssetBaseValue(assetBaseValue);
        Assert.assertEquals(1, assetBaseValue.getId());

        Assert.assertEquals(Collections.singletonList(assetBaseValue),
                store.queryAssetBaseValues());
        Assert.assertEquals(assetBaseValue, store.queryAssetBaseValue(1));

        store.updateAssetBaseValueEnabled(1, true);
        assetBaseValue.setEnabled(true);
        Assert.assertEquals(assetBaseValue, store.queryAssetBaseValue(1));

        AssetHistoryBaseValue historyBaseValue = new AssetHistoryBaseValue(0, "a1", "a2",
                10, 10, 20, "t", 1000);
        store.storeAssetHistoryBaseValue(historyBaseValue);

        assetBaseValue.setBaseValue(20);
        Assert.assertEquals(assetBaseValue, store.queryAssetBaseValue(1));

        historyBaseValue.setId(1);
        Assert.assertEquals(Collections.singletonList(historyBaseValue),
                store.queryAssetHistoryBaseValue(0, 2000, 10));

        store.deleteAssetBaseValue(1);
    }
}
