package org.eurekaka.bricks.common.model;

public enum OrderSide {

    ALL,
    BUY, SELL,
    // test only
    NONE,

    // 单向持仓
    // 平空
    BUY_SHORT,
    // 平多
    SELL_LONG,

    // options only
    CALL,
    PUT,
}
