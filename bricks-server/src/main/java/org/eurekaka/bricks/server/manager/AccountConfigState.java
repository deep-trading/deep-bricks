package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.store.AccountConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * account config存储管理器，需要支持动态更新部分配置参数
 */
public class AccountConfigState {
    private static final Logger logger = LoggerFactory.getLogger(AccountConfigState.class);

    private final AccountConfigStore store;
    private final Map<Integer, AccountConfig> configMap;

    public AccountConfigState(AccountConfigStore store) {
        this.store = store;

        this.configMap = new ConcurrentHashMap<>();

        try {
            for (AccountConfig config : store.query()) {
                if (config.isEnabled()) {
                    configMap.put(config.getId(), config);
                }
            }
        } catch (StoreException e) {
            throw new InitializeException("failed to load account configs", e);
        }
    }

    public void updateAccountConfig(AccountConfig config) throws StoreException {
        if (config.getId() == 0) {
            store.store(config);
            logger.info("store new account config: {}", config);
        } else {
            store.update(config);
        }
        if (configMap.containsKey(config.getId())) {
            logger.info("update config: {}", config);
            configMap.get(config.getId()).copy(config);
        }
    }

    public List<AccountConfig> queryAll() throws StoreException {
        List<AccountConfig> configs = store.query();
        for (AccountConfig config : configs) {
            config.setAuthKey(Utils.maskKeySecret(config.getAuthKey()));
            config.setAuthSecret(Utils.maskKeySecret(config.getAuthSecret()));
        }
        return configs;
    }

    public AccountConfig queryAccountConfig(int id) throws StoreException {
        return store.query(id);
    }

    // !!! 必须在外部检查是否可以disable？？？
    public void disableAccountConfig(int id) throws StoreException {
        if (configMap.containsKey(id)) {
            configMap.remove(id);
            store.updateEnabled(id, false);
            logger.info("disable account config, id: {}", id);
        }
    }

    public void enableAccountConfig(int id) throws StoreException {
        if (!configMap.containsKey(id)) {
            AccountConfig config = store.query(id);
            if (config != null) {
                config.setEnabled(true);
                store.updateEnabled(id, true);
                configMap.put(id, config);
                logger.info("enable account config: {}", config);
            }
        }
    }

    public void deleteAccountConfig(int id) throws StoreException {
        if (configMap.containsKey(id)) {
            throw new StoreException("can not delete enabled account config, id: " + id);
        }
        store.delete(id);
    }

    public AccountConfig getAccountConfig(int id) {
        return configMap.get(id);
    }

    public List<AccountConfig> getAccountConfigs() {
        return new ArrayList<>(configMap.values());
    }

//    public AccountConfig getAccountConfig(int id) {
//        return configMap.get(id);
//    }
}
