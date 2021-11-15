package org.eurekaka.bricks.common.model;

public enum  OrderType {

    // market order type
    MARKET,

    // limit order type
    LIMIT,
    LIMIT_MOCK,
    LIMIT_MAKER,

    // reference from binance
    // Good Till Crossing 无法成为挂单方就撤销
    LIMIT_GTX,
    // Immediate or Cancel 无法立即成交(吃单)的部分就撤销
    LIMIT_IOC,
    // GTC - Good Till Cancel 成交为止
    LIMIT_GTC,
    // Fill or Kill 无法全部立即成交就撤销
    LIMIT_FOK,

    // 未知类型
    NONE,
    // 可以继续支持条件订单
}
