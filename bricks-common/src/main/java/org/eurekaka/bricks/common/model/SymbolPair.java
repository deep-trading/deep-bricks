package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class SymbolPair {
    public final String name;
    public final String symbol;

    public SymbolPair(String name, String symbol) {
        this.name = name;
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return "SymbolPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SymbolPair)) return false;
        SymbolPair that = (SymbolPair) o;
        return name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol);
    }
}
