package org.eurekaka.bricks.server.model;

public enum OrderState {
    NONE,
    // 正在提交新的订单
    SUBMITTING,
    // 该订单已经提交
    SUBMITTED,
    // 订单正在撤销
    CANCELLING,
    // 订单已经撤销
    CANCELLED,
}
