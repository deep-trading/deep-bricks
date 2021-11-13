package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.model.StrategyConfig;

public class TestStrategyFactory implements StrategyFactory {
    @Override
    public Strategy createStrategy(StrategyConfig strategyConfig) {
        return new TestStrategy(strategyConfig.getName());
    }
}
