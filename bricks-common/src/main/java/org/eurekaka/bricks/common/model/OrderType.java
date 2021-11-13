package org.eurekaka.bricks.common.model;

public enum  OrderType {

    // market order type
    MARKET,

    // limit order type
    LIMIT,
    LIMIT_MOCK,
    LIMIT_MAKER,

    LIMIT_IOC,

    // 未知类型
    NONE,
    // 可以继续支持条件订单
}
