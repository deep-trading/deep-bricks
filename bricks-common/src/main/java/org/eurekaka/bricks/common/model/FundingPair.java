package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class FundingPair {
    public final String name;
    public final String symbol;
    public final long lastTime;

    public FundingPair(String name, String symbol, long lastTime) {
        this.name = name;
        this.symbol = symbol;
        this.lastTime = lastTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FundingPair)) return false;
        FundingPair that = (FundingPair) o;
        return lastTime == that.lastTime &&
                name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, lastTime);
    }

    @Override
    public String toString() {
        return "FundingPair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", lastTime=" + lastTime +
                '}';
    }
}
