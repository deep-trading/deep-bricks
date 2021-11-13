package org.eurekaka.bricks.server.state;

import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.store.StrategyConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StrategyConfigState {
    private final static Logger logger = LoggerFactory.getLogger(StrategyConfigState.class);

    private final StrategyConfigStore store;
    private final Map<Integer, StrategyConfig> configMap;

    public StrategyConfigState(StrategyConfigStore store) {
        this.store = store;
        this.configMap = new ConcurrentHashMap<>();

        try {
            for (StrategyConfig config : store.query()) {
                if (config.isEnabled()) {
                    configMap.put(config.getId(), config);
                }
            }
        } catch (StoreException e) {
            throw new InitializeException("failed to load strategy configs", e);
        }
    }

    public void update(StrategyConfig config) throws StoreException {
        if (config.getId() == 0) {
            store.store(config);
            logger.info("store new strategy config: {}", config);
        } else {
            store.update(config);
        }
        if (configMap.containsKey(config.getId())) {
            logger.info("update strategy config: {}", config);
            configMap.get(config.getId()).copy(config);
        }
    }

    public List<StrategyConfig> query() throws StoreException {
        return store.query();
    }

    public StrategyConfig query(int id) throws StoreException {
        return store.query(id);
    }

    // !!! 必须在外部检查是否可以disable？？？
    public void disable(int id) throws StoreException {
        if (configMap.containsKey(id)) {
            configMap.remove(id);
            store.updateEnabled(id, false);
            logger.info("disable strategy config, id: {}", id);
        }
    }

    public void enable(int id) throws StoreException {
        if (!configMap.containsKey(id)) {
            StrategyConfig config = store.query(id);
            if (config != null) {
                config.setEnabled(true);
                store.updateEnabled(id, true);
                configMap.put(id, config);
                logger.info("enable strategy config: {}", config);
            }
        }
    }

    public void delete(int id) throws StoreException {
        if (configMap.containsKey(id)) {
            throw new StoreException("can not delete enabled strategy config, id: " + id);
        }
        store.delete(id);
    }

    public StrategyConfig get(int id) {
        return configMap.get(id);
    }

    public List<StrategyConfig> get() {
        return new ArrayList<>(configMap.values());
    }
}
