package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RiskLimitValue {

    @JsonProperty("total_balance")
    public final double totalBalance;

    @JsonProperty("available_balance")
    public final double availableBalance;

    @JsonProperty("position_risk_limit_values")
    public final List<PositionRiskLimitValue> positionRiskLimitValues;

    public RiskLimitValue(double totalBalance, double availableBalance,
                          List<PositionRiskLimitValue> positionRiskLimitValues) {
        this.totalBalance = totalBalance;
        this.availableBalance = availableBalance;
        this.positionRiskLimitValues = positionRiskLimitValues;
    }

    @Override
    public String toString() {
        return "RiskLimitValue{" +
                "totalBalance=" + totalBalance +
                ", availableBalance=" + availableBalance +
                ", positionRiskLimitValues=" + positionRiskLimitValues +
                '}';
    }
}
