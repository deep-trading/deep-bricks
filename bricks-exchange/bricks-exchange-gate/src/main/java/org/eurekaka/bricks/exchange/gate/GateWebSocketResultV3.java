package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class GateWebSocketResultV3 {

    @JsonAlias("contract")
    @JsonProperty("s")
    public String symbol;
    @JsonProperty("U")
    public long firstUpdateId;
    @JsonProperty("u")
    public long lastUpdateId;
    @JsonAlias({"bids", "b"})
//    @JsonProperty("b")
    public List<GatePriceSizePair> bids;
    @JsonAlias({"asks", "a"})
//    @JsonProperty("a")
    public List<GatePriceSizePair> asks;

    public GateWebSocketResultV3() {
    }
}
