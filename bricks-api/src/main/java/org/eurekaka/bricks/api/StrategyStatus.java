package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;

public interface StrategyStatus<T> {

    /**
     * 获取策略状态
     * @param name 策略名称
     * @throws StrategyException 加载失败
     */
    T get(String name) throws StrategyException;


    /**
     * 更新策略状态
     * @param status 策略状态
     * @throws StrategyException 执行失败
     */
    void put(T status) throws StrategyException;

    /**
     * 初始化策略状态
     * @param status 初始策略状态
     * @throws StrategyException 执行失败
     */
    void init(T status) throws StrategyException;
}
