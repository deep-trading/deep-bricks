package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class CancelOrderPair {
    public final String name;
    public final String symbol;
    public final String orderId;

    public CancelOrderPair(String name, String symbol, String orderId) {
        this.name = name;
        this.symbol = symbol;
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CancelOrderPair)) return false;
        CancelOrderPair that = (CancelOrderPair) o;
        return name.equals(that.name) &&
                symbol.equals(that.symbol) &&
                orderId.equals(that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, orderId);
    }

    @Override
    public String toString() {
        return "CancelOrderPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}
