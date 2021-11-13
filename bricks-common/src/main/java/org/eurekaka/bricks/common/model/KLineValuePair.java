package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class KLineValuePair {
    public final String name;
    public final String symbol;
    public final long startTime;
    public final long stopTime;
    public final KLineInterval interval;
    public final int limit;

    public KLineValuePair(String name, String symbol, int limit) {
        this.name = name;
        this.symbol = symbol;
        this.limit = limit;
        this.startTime = 0;
        this.stopTime = 0;
        this.interval = KLineInterval._1M;
    }

    public KLineValuePair(String name, String symbol,
                          long startTime, long stopTime,
                          KLineInterval interval, int limit) {
        this.name = name;
        this.symbol = symbol;
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.interval = interval;
        this.limit = limit;
    }

    @Override
    public String toString() {
        return "KLineValuePair{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", startTime=" + startTime +
                ", stopTime=" + stopTime +
                ", interval=" + interval +
                ", limit=" + limit +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KLineValuePair)) return false;
        KLineValuePair that = (KLineValuePair) o;
        return startTime == that.startTime && stopTime == that.stopTime &&
                limit == that.limit && name.equals(that.name) &&
                symbol.equals(that.symbol) && interval == that.interval;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, startTime, stopTime, interval, limit);
    }
}
