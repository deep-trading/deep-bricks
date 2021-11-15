package org.eurekaka.bricks.common.model;

import java.util.Objects;

/**
 * 统一发送给exchange的action data
 */
public class ActionPair {
    public final String name;
    public final String symbol;

    private String orderId;

    public ActionPair(String name, String symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    public ActionPair(String name, String symbol, String orderId) {
        this.name = name;
        this.symbol = symbol;
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActionPair)) return false;
        ActionPair that = (ActionPair) o;
        return name.equals(that.name) && symbol.equals(that.symbol) && Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, orderId);
    }

    @Override
    public String toString() {
        return "ActionPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}
