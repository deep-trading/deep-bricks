package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PositionRiskLimitValue {
    private String name;

    public final String symbol;
    public final int leverage;
    @JsonProperty("limit_value")
    public final long limitValue;
    public final long position;
    @JsonProperty("init_value")
    public final double initValue;
    @JsonProperty("maintain_value")
    public final double maintainValue;

    public PositionRiskLimitValue(String symbol, int leverage, long limitValue,
                                  long position, double initValue, double maintainValue) {
        this.symbol = symbol;
        this.leverage = leverage;
        this.position = position;
        this.limitValue = limitValue;
        this.initValue = initValue;
        this.maintainValue = maintainValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "PositionRiskLimitValue{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", leverage=" + leverage +
                ", position=" + position +
                ", limitValue=" + limitValue +
                ", initValue=" + initValue +
                ", maintainValue=" + maintainValue +
                '}';
    }
}
