package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class ExAction<T> {
    private final ActionType type;
    private final T data;

    public ExAction(ActionType type) {
        this.type = type;
        this.data = null;
    }

    public ExAction(ActionType type, T data) {
        this.type = type;
        this.data = data;
    }

    public ActionType getType() {
        return type;
    }

    public T getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExAction)) return false;
        ExAction<?> exAction = (ExAction<?>) o;
        return type == exAction.type &&
                Objects.equals(data, exAction.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, data);
    }

    public enum ActionType {
        // 注册websocket
        ADD_SYMBOL,
        REMOVE_SYMBOL,
        REGISTER_QUEUE,
//        ,
        // public market
        GET_KLINE,

        // 返回所有的symbol信息
        GET_SYMBOLS,
        MAKE_ORDER,
        CANCEL_ORDER,
        // 获取所有交易对余额
        GET_BALANCES,
        // 获取单个交易对的余额
        GET_BALANCE,
        GET_CURRENT_ORDER,
        GET_BID_DEPTH_PRICE,
        GET_ASK_DEPTH_PRICE,

        // 转换统一计价货币参数
        GET_MARK_USDT,

        // futures
        // 返回标的的标记价格（指数价格？）
        GET_NET_VALUES,
        GET_NET_VALUE,
        // 获取所有的交易对仓位信息
        GET_POSITIONS,
        // 获取单个交易对的仓位信息
        GET_POSITION,
        GET_FUNDING_FEES,
        GET_FUNDING_RATE,
        GET_RISK_LIMIT,
        UPDATE_RISK_LIMIT,

        // 提款资产
        WITHDRAW_ASSET,
        // 提取资产记录
        GET_ASSET_RECORDS,
        // 内部转账
        TRANSFER_ASSET,

        // v2 代表异步接口
        // v2 订单操作使用client order id
        MAKE_ORDER_V2,
        CANCEL_ORDER_V2,
        GET_CURRENT_ORDER_V2,
        GET_ORDER_V2,

        GET_RISK_LIMIT_V2,
        UPDATE_RISK_LIMIT_V2,
        WITHDRAW_ASSET_V2,
        GET_ASSET_RECORDS_V2,
        TRANSFER_ASSET_V2,
    }

}
