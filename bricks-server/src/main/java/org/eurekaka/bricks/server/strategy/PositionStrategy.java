package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.ExAction;
import org.eurekaka.bricks.common.model.ExMessage;
import org.eurekaka.bricks.common.model.PositionValue;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.store.FutureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PositionStrategy implements Strategy {
    private final Logger logger = LoggerFactory.getLogger(PositionStrategy.class);

    protected final BrickContext brickContext;
    protected final StrategyConfig strategyConfig;
    protected final FutureStore store;

    private int type;

    public PositionStrategy(BrickContext brickContext, StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;

        this.store = new FutureStore();
    }

    public PositionStrategy(BrickContext brickContext,
                            StrategyConfig strategyConfig,
                            FutureStore store) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;
        this.store = store;
    }

    @Override
    public void start() throws StrategyException {
        type = strategyConfig.getInt("type", 2);

        logger.info("position strategy started");
    }

    @Override
    public long nextTime() {
        return System.currentTimeMillis() / 60000 * 60000 + 60000;
    }

    @Override
    public void run() throws StrategyException {
        List<PositionValue> positionValues = new ArrayList<>();
        for (Exchange ex : brickContext.getAccountManager().getAccounts(type)) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get positions", (Throwable) msg.getData());
            }
            positionValues.addAll((List<PositionValue>) msg.getData());
        }

        long currentMinute = System.currentTimeMillis() / 60000 * 60000;
        try {
            for (PositionValue value : positionValues) {
                value.setTime(currentMinute);
                store.storePositionValue(value);
            }
        } catch (StoreException e) {
            throw new StrategyException("failed to store positions", e);
        }

        processPositions(positionValues);
    }

    protected void processPositions(List<PositionValue> positionValues) throws StrategyException {}
}
