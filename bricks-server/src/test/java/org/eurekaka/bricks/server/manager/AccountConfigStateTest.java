package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.server.store.AccountConfigStore;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

public class AccountConfigStateTest {

    @Test
    public void testAccountConfigState() throws StoreException {
        AccountConfigStore store = Mockito.mock(AccountConfigStore.class);
        AccountConfig c1 = new AccountConfig(1, "n1", (short) 1,
                "c1", "l1", "ac1", "w1", "u1",
                "u2", "ak1", "as1", true);
        Mockito.when(store.query()).thenReturn(Collections.singletonList(c1));

        AccountConfigState state = new AccountConfigState(store);
        Assert.assertEquals(Collections.singletonList(c1), state.getAccountConfigs());

        AccountConfig c2 = new AccountConfig(0, "n2", (short) 2,
                "c1", "l1", "ac1", "w1", "u1",
                "u2", "ak1", "as1", false);

        Mockito.when(store.query(2)).thenReturn(c2);
        state.enableAccountConfig(2);

        state.disableAccountConfig(2);
        state.deleteAccountConfig(2);
    }
}
