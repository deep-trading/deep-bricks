package org.eurekaka.bricks.exchange.bhex;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BhexWebsocketMsg {

    @JsonProperty("e")
    public String event;

    @JsonProperty("s")
    public String symbol;

    @JsonProperty("S")
    public String direction;

    @JsonProperty("p")
    public double avgPrice;

    @JsonProperty("P")
    public int totalSize;

    @JsonProperty("a")
    public int availSize;

    public long pong;

    public BhexWebsocketMsg() {
    }
}
