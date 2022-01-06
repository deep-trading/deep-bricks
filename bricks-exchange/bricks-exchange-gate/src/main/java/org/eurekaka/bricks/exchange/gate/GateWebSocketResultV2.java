package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GateWebSocketResultV2 {

    @JsonProperty("s")
    public String contract;
    @JsonProperty("t")
    public long time;

    @JsonProperty("b")
    public double bidPrice;
    @JsonProperty("B")
    public int bidSize;
    @JsonProperty("a")
    public double askPrice;
    @JsonProperty("A")
    public int askSize;

    public GateWebSocketResultV2() {
    }
}
