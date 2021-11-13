package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StoreException;

public interface StrategyStore<T> {
    /**
     * 存储策略状态
     * @param value 策略状态值
     * @throws StoreException 存储失败
     */
    void store(T value) throws StoreException;

    /**
     * 查询策略状态
     * @param name 策略名称
     * @return 策略状态值
     * @throws StoreException 查询失败
     */
    T query(String name) throws StoreException;
}
