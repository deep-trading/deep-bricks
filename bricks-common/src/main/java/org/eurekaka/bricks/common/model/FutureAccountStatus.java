package org.eurekaka.bricks.common.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FutureAccountStatus extends AccountStatus {

    private final Map<String, PositionValue> positionValues;
    private final Map<String, Double> fundingRates;

    public FutureAccountStatus() {
        this.positionValues = new ConcurrentHashMap<>();
        this.fundingRates = new ConcurrentHashMap<>();
    }

    public Map<String, PositionValue> getPositionValues() {
        return positionValues;
    }

    public Map<String, Double> getFundingRates() {
        return fundingRates;
    }
}
