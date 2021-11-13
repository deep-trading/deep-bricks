package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.common.model.StrategyConfig;

public interface StrategyManager {

    void start();

    void stop();

    void startStrategy(StrategyConfig strategyConfig) throws StrategyException;

    void stopStrategy(StrategyConfig strategyConfig) throws StrategyException;

    default void notifyStrategy(Notification notification) throws StrategyException {}
}
