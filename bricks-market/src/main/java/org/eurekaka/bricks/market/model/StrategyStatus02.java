package org.eurekaka.bricks.market.model;

import org.eurekaka.bricks.api.StrategyStatus;
import org.eurekaka.bricks.api.StrategyStore;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.exception.StrategyException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StrategyStatus02 implements StrategyStatus<StrategyValue02> {
    private final Map<String, StrategyValue02> strategyStatus;
    private final StrategyStore<StrategyValue02> store;

    public StrategyStatus02(StrategyStore<StrategyValue02> store) {
        this.store = store;

        this.strategyStatus = new ConcurrentHashMap<>();
    }

    @Override
    public StrategyValue02 get(String name) throws StrategyException {
        return strategyStatus.get(name);
    }

    @Override
    public void put(StrategyValue02 status) throws StrategyException {
        try {
            store.store(status);
            strategyStatus.put(status.name, status);
        } catch (StoreException e) {
            throw new StrategyException("failed to store strategy value: " + status, e);
        }
    }

    @Override
    public void init(StrategyValue02 status) throws StrategyException {
        try {
            StrategyValue02 value = store.query(status.name);
            if (value != null) {
                strategyStatus.put(value.name, value);
            } else {
                strategyStatus.put(status.name, status);
                store.store(status);
            }
        } catch (StoreException e) {
            throw new StrategyException("failed to init strategy value", e);
        }
    }
}
