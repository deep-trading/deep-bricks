package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;
import org.eurekaka.bricks.server.store.AssetBaseValueStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

public class AccountAssetStateTest {

    @Test
    public void testAccountAssetState() throws StoreException {
        AssetBaseValueStore store = Mockito.mock(AssetBaseValueStore.class);
        AssetBaseValue v1 = new AssetBaseValue(1, "a1", "a2", 10, true);

        Mockito.when(store.queryAssetBaseValues()).thenReturn(Collections.singletonList(v1));

        AccountAssetState state = new AccountAssetState(store);
        Assert.assertEquals(1, state.queryAllAccountBaseValues().size());

        AssetBaseValue v2 = new AssetBaseValue(2, "a1", "a3", 20, false);
        state.addAccountBaseValue(v2);
        AssetHistoryBaseValue hv1 = new AssetHistoryBaseValue(0, "a1", "a3",
                20, 20, 40, "c1", 1);
        Mockito.when(store.queryAssetBaseValue(2)).thenReturn(v2);
        state.enableAccountBaseValue(2);
        Assert.assertTrue(v2.isEnabled());
        state.updateAccountBaseValue(hv1);
        Assert.assertEquals(v2.getBaseValue(), state.getAccountAssetBaseValue("a3", "a1"), 0.01);

        state.disableAccountBaseValue(2);
        Assert.assertFalse(state.hasAccountAssetBaseValue("a3", "a1"));
        state.deleteAccountBaseValue(2);
    }
}
