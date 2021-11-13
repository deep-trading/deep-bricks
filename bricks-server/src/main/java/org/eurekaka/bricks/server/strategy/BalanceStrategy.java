package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.AccountProfit;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.store.BalanceStore;

import java.util.List;

public class BalanceStrategy implements Strategy {

    protected final BrickContext brickContext;
    protected final StrategyConfig strategyConfig;
    protected final BalanceStore store;

    public BalanceStrategy(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.store = new BalanceStore();
    }

    @Override
    public void run() throws StrategyException {
        List<AccountProfit> profits;
        try {
            profits = brickContext.getAccountProfit();
        } catch (ServiceException e) {
            throw new StrategyException("failed to get account profit", e);
        }
        try {
            for (AccountProfit profit : profits) {
                store.storeAccountProfit(profit);
            }
        } catch (StoreException e) {
            throw new StrategyException("failed to store account profit", e);
        }
        processProfit(profits);
    }

    @Override
    public long nextTime() {
        return System.currentTimeMillis() / 60000 * 60000 + 60000;
    }

    protected void processProfit(List<AccountProfit> profits) throws StrategyException {}
}
