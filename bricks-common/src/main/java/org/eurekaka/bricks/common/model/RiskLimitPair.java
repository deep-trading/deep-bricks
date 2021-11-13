package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class RiskLimitPair {
    public final String name;
    public final String symbol;
    // 合约风险控制有两种
    // 1. 杠杆倍数，通过杠杆倍数控制最大持仓上限
    // 2. 风险限额，直接控制风险限额值
    public final int leverage;

    public RiskLimitPair(String name, String symbol, int leverage) {
        this.name = name;
        this.symbol = symbol;
        this.leverage = leverage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RiskLimitPair)) return false;
        RiskLimitPair that = (RiskLimitPair) o;
        return leverage == that.leverage &&
                name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, leverage);
    }

    @Override
    public String toString() {
        return "RiskLimitPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", leverage=" + leverage +
                '}';
    }
}
