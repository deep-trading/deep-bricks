package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class DepthPricePair {
    public static final int ZERO_DEPTH_QTY = 0;

    public final String name;
    public final String symbol;

    // 最小深度要求
    public final int depthQty;

    public DepthPricePair(String name, String symbol, int depthQty) {
        this.name = name;
        this.symbol = symbol;
        this.depthQty = depthQty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DepthPricePair)) return false;
        DepthPricePair that = (DepthPricePair) o;
        return depthQty == that.depthQty &&
                name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, depthQty);
    }

    @Override
    public String toString() {
        return "DepthPricePair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", depthQty=" + depthQty +
                '}';
    }
}
