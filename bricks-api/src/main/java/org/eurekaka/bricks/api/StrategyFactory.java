package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.StrategyConfig;

/**
 * 根据策略配置的交易对信息，创建策略
 */
public interface StrategyFactory {

    /**
     * 根据策略配置，创建对应的策略
     * @param strategyConfig 策略配置
     * @return 策略
     */
    Strategy createStrategy(StrategyConfig strategyConfig) throws StrategyException;

    default void startFactory() throws StrategyException {}

    default void shutdown() throws StrategyException {}
}
