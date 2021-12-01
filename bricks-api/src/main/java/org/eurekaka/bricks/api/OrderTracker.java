package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.CurrentOrder;
import org.eurekaka.bricks.common.model.Order;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface OrderTracker {

    /**
     * 初始化tracker，tracker有状态时，需要在策略启动时初始化
     * @throws StrategyException 执行失败
     */
    void init(List<CurrentOrder> orders) throws StrategyException;

    /**
     * 跟随strategy run()，定时检查当前所有订单（异步）
     * @throws StrategyException 执行失败
     */
    void track() throws StrategyException;

    /**
     * 提交需要跟踪的订单
     * @param order 待跟踪订单
     * @throws StrategyException 执行失败
     */
    CompletableFuture<CurrentOrder> submit(Order order) throws StrategyException;
}
