package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class GateWebSocketResultV3 {

    @JsonProperty("s")
    public String symbol;
    @JsonProperty("U")
    public long firstUpdateId;
    @JsonProperty("u")
    public long lastUpdateId;
    @JsonProperty("b")
    public List<GatePriceSizePair> bids;
    @JsonProperty("a")
    public List<GatePriceSizePair> asks;

    public GateWebSocketResultV3() {
    }
}
