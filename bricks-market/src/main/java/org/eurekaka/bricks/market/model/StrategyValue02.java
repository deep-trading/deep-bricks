package org.eurekaka.bricks.market.model;

import java.util.Objects;

public class StrategyValue02 {

    // 仓位名称
    public final String name;
    // 仓位数量
    public final double value;

    public final long time;

    public StrategyValue02(String name, double value, long time) {
        this.name = name;
        this.value = value;
        this.time = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StrategyValue02)) return false;
        StrategyValue02 value02 = (StrategyValue02) o;
        return Double.compare(value02.value, value) == 0 && time == value02.time && name.equals(value02.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value, time);
    }

    @Override
    public String toString() {
        return "StrategyValue02{" +
                "name='" + name + '\'' +
                ", value=" + value +
                ", time=" + time +
                '}';
    }
}
