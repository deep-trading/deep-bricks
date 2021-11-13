package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GateWebSocketOrderBook {
    @JsonProperty("p")
    public double price;
    @JsonProperty("s")
    public long size;
    @JsonProperty("c")
    public String contract;

    public GateWebSocketOrderBook() {
    }
}
