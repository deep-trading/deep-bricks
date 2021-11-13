package org.eurekaka.bricks.server.service;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.common.model.InfoState;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.manager.StrategyManager;
import org.eurekaka.bricks.server.state.StrategyConfigState;

import java.util.List;

public class StrategyService {

    protected final StrategyConfigState state;
    protected final InfoState<Info0, ?> infoState;
    private final StrategyManager strategyManager;

    public StrategyService(BrickContext brickContext) {
        this.state = brickContext.getStrategyConfigState();
        this.infoState = brickContext.getInfoState();
        this.strategyManager = brickContext.getStrategyManager();
    }

    public List<StrategyConfig> query() throws ServiceException {
        try {
            return state.query();
        } catch (StoreException e) {
            throw new ServiceException("failed to query strategy configs", e);
        }
    }

    public StrategyConfig store(StrategyConfig config) throws ServiceException {
        checkStore(config);
        try {
            state.update(config);
            return config;
        } catch (StoreException e) {
            throw new ServiceException("failed to store strategy config: " + config, e);
        }
    }

    public void update(StrategyConfig config) throws ServiceException {
        checkUpdate(config);
        try {
            state.update(config);
        } catch (StoreException e) {
            throw new ServiceException("failed to update strategy config: " + config, e);
        }
    }

    public void delete(int id) throws ServiceException {
        try {
            state.delete(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to delete strategy config: " + id, e);
        }
    }

    public void enable(int id) throws ServiceException {
        StrategyConfig config;
        try {
            config = state.query(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to enable strategy config, not found: " + id, e);
        }
        if (config != null && !config.isEnabled()) {
            // 检查infoName是否已经存在
            if (!"global".equals(config.getInfoName())) {
                List<Info0> infos = infoState.getInfoByName(config.getInfoName());
                if (infos.isEmpty()) {
                    throw new ServiceException("no enabled info found: " + config);
                }
            }
            processEnable(config);
            try {
                state.enable(id);
                try {
                    strategyManager.startStrategy(state.get(id));
                } catch (StrategyException e) {
                    state.disable(id);
                    throw new ServiceException("failed to start strategy", e);
                }
            } catch (StoreException e) {
                throw new ServiceException("failed to enable strategy config", e);
            }
        }
    }

    public void disable(int id) throws ServiceException {
        StrategyConfig config = state.get(id);
        if (config != null) {
//            if ("global".equals(config.getInfoName())) {
//                // 全局策略不能disable
//                throw new ServiceException("can not disable global strategy: " + config.getName());
//            }
            processDisable(config);
            try {
                strategyManager.stopStrategy(config);
            } catch (StrategyException e) {
                throw new ServiceException("failed to stop strategy", e);
            }
            try {
                state.disable(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to disable strategy config", e);
            }
        }
    }

    protected void processEnable(StrategyConfig config) throws ServiceException {}

    protected void processDisable(StrategyConfig config) throws ServiceException {}

    protected void checkStore(StrategyConfig config) throws ServiceException {}

    protected void checkUpdate(StrategyConfig config) throws ServiceException {}
}
