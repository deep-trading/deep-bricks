package org.eurekaka.bricks.server.store;

import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.util.Utils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountConfigStoreTest extends StoreTestBase {

    @Test
    public void testAccountConfigStore() throws StoreException {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("timezone", "+08");
        configMap.put("auth_password", "pass");
        configMap.put("auth_salt", "salt");
        configMap.put("app_name", "app");
        Utils.checkConfig(ConfigFactory.parseMap(configMap));

        AccountConfigStore store = new AccountConfigStore();

        AccountConfig accountConfig = new AccountConfig(0, "a1", (short) 1,
                "clz", "lclz", "aclz", "w1", "u1",
                null, "authKey", "authSecret", false);
        accountConfig.setPriority((short) 3);
        accountConfig.setTakerRate(0.00005);

        store.store(accountConfig);
        Assert.assertEquals(1, accountConfig.getId());

        List<AccountConfig> accountConfigs = store.query();
        Assert.assertEquals(Collections.singletonList(accountConfig), accountConfigs);
        Assert.assertEquals(accountConfig, accountConfigs.get(0));

        Assert.assertEquals(accountConfig, store.query(1));

        accountConfig.setMakerRate(0.00002);
        store.update(accountConfig);
        Assert.assertEquals(accountConfig, store.query(1));

        store.updateEnabled(1, true);
        accountConfig.setEnabled(true);
        Assert.assertEquals(accountConfig, store.query(1));

        store.delete(1);
    }
}
