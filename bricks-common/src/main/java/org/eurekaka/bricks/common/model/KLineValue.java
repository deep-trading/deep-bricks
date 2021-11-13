package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class KLineValue {

    public final long time;
    public final String name;
    public final String symbol;
    public final double open;
    public final double close;
    public final double highest;
    public final double lowest;
    public final double volume;

    public KLineValue(long time, String name, String symbol, double open, double close,
                      double highest, double lowest, double volume) {
        this.time = time;
        this.name = name;
        this.symbol = symbol;
        this.open = open;
        this.close = close;
        this.highest = highest;
        this.lowest = lowest;
        this.volume = volume;
    }

    @Override
    public String toString() {
        return "KLineValue{" +
                "time=" + time +
                ", symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", open=" + open +
                ", close=" + close +
                ", highest=" + highest +
                ", lowest=" + lowest +
                ", volume=" + volume +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KLineValue)) return false;
        KLineValue that = (KLineValue) o;
        return time == that.time &&
                Double.compare(that.open, open) == 0 &&
                Double.compare(that.close, close) == 0 &&
                Double.compare(that.highest, highest) == 0 &&
                Double.compare(that.lowest, lowest) == 0 &&
                Double.compare(that.volume, volume) == 0 &&
                symbol.equals(that.symbol) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, symbol, name, open, close, highest, lowest, volume);
    }
}
