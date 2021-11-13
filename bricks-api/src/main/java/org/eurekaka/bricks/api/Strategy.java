package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Notification;

import java.util.List;

/**
 * 定时策略执行
 */
public interface Strategy {
    default void start() throws StrategyException {}

    default void stop() throws StrategyException {}

    void run() throws StrategyException;

    default void notify(Notification notification) throws StrategyException {}

    default long nextTime() {
        return 0;
    }
}
