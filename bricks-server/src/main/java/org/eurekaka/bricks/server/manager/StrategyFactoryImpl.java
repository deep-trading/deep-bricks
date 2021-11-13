package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class StrategyFactoryImpl implements StrategyFactory {
    private final BrickContext brickContext;

    public StrategyFactoryImpl(BrickContext brickContext) {
        this.brickContext = brickContext;
    }

    @Override
    public Strategy createStrategy(StrategyConfig strategyConfig) throws StrategyException {
        try {
            Class<?> clz = Class.forName(strategyConfig.getClz());
            Constructor<?> constructor = null;
            try {
                constructor = clz.getConstructor(BrickContext.class, StrategyConfig.class);
            } catch (NoSuchMethodException e) {
                constructor = clz.getConstructor(brickContext.getClass(), StrategyConfig.class);
            }
            return (Strategy) constructor.newInstance(brickContext, strategyConfig);
        }  catch (ClassNotFoundException | NoSuchMethodException |
                InstantiationException | IllegalAccessException |
                InvocationTargetException e) {
            throw new StrategyException("failed to create strategy: " + strategyConfig.getName(), e);
        }
    }
}
