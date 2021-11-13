package org.eurekaka.bricks.server.store;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

public class StoreTestBase {
    @Before
    public void setUp() throws Exception {
        Map<String, String> configMap = new HashMap<>();
        configMap.put("url", "jdbc:h2:./target/test" + System.currentTimeMillis());
        configMap.put("driver", "org.h2.Driver");

        Config config = ConfigFactory.parseMap(configMap);

        DatabaseStore.setDataSource(DatabaseStore.getDatabaseSource(config));

        DatabaseStore.initSql("sql/init.sql");
    }

    @After
    public void tearDown() throws Exception {
        DatabaseStore.close();
    }
}
