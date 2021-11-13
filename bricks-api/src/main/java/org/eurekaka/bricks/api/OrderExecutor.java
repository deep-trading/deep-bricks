package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.OrderException;
import org.eurekaka.bricks.common.model.*;

/**
 * 下单执行器
 * 异步下单，不影响策略计算
 */
public interface OrderExecutor {

    /**
     * 执行器初始化
     */
    void start();

    /**
     * 退出执行器
     */
    void stop();

    /**
     * 下单，生成计划订单
     * @param name 交易对名称
     * @param quantity 对冲美金数量，正数代表买入，负数代表卖出
     * @param symbolPrice 生成对冲时，交易对的价格
     */
    void makeOrder(String name, long quantity, long symbolPrice);

    /**
     * 下单，执行计划订单
     * @param planOrder 计划订单
     */
    void makeOrder(PlanOrder planOrder);

    /**
     * 下单
     * @param order 已经生成的订单，待直接执行
     * @return true 代表执行成功，更新order内的 orderId，false代表执行失败
     */
    boolean makeOrder(Order order);

    /**
     * 更新当前订单信息
     * websocket消息通知
     * @param notification 当前订单状态
     */
    default void notify(Notification notification) {}


    default boolean notBusy() {
        return false;
    }
}
