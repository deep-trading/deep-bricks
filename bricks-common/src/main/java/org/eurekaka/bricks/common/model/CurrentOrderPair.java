package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class CurrentOrderPair {
    public final String name;
    public final String symbol;
    public final int type;

    public CurrentOrderPair(String name, String symbol) {
        this.name = name;
        this.symbol = symbol;
        this.type = 0;
    }

    public CurrentOrderPair(String name, String symbol, int type) {
        this.name = name;
        this.symbol = symbol;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurrentOrderPair)) return false;
        CurrentOrderPair that = (CurrentOrderPair) o;
        return type == that.type &&
                name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, type);
    }

    @Override
    public String toString() {
        return "CurrentOrderPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", type=" + type +
                '}';
    }
}
