package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.ExAction;
import org.eurekaka.bricks.common.model.ExMessage;
import org.eurekaka.bricks.common.model.FundingValue;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.store.FutureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class FundingStrategy implements Strategy {
    private final Logger logger = LoggerFactory.getLogger(FundingStrategy.class);
    private final long HOUR_MILLISECONDS = 3600000;

    protected final BrickContext brickContext;
    protected final StrategyConfig strategyConfig;
    protected final FutureStore store;

    private int type;
    private int delay;

    public FundingStrategy(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.store = new FutureStore();
    }

    @Override
    public void start() throws StrategyException {
        type = strategyConfig.getInt("type", 2);
        delay = strategyConfig.getInt("delay", 600000);

        logger.info("funding strategy started.");
    }

    @Override
    public void run() throws StrategyException {
        List<FundingValue> fundingValues = new ArrayList<>();
        for (Exchange ex : brickContext.getAccountManager().getAccounts(type)) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_FEES,
                    System.currentTimeMillis() - HOUR_MILLISECONDS));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get funding values", (Throwable) msg.getData());
            }
            fundingValues.addAll((List<FundingValue>) msg.getData());
        }

        try {
            for (FundingValue fundingValue : fundingValues) {
                store.storeFundingValue(fundingValue);
            }
        } catch (StoreException e) {
            throw new StrategyException("failed to store funding values", e);
        }

        processFundingValues(fundingValues);
    }

    @Override
    public long nextTime() {
        return System.currentTimeMillis() / HOUR_MILLISECONDS * HOUR_MILLISECONDS + HOUR_MILLISECONDS + delay;
    }

    protected void processFundingValues(List<FundingValue> fundingValues) throws StrategyException {}
}
