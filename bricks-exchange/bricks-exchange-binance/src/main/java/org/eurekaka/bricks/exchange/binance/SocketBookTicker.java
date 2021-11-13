package org.eurekaka.bricks.exchange.binance;

import com.fasterxml.jackson.annotation.JsonProperty;

class SocketBookTicker {

    @JsonProperty("e")
    public String eventName;
    @JsonProperty("E")
    public long eventTime;

    @JsonProperty("s")
    public String symbol;

    @JsonProperty("b")
    public double bidPrice;
    @JsonProperty("B")
    public double bidSize;
    @JsonProperty("a")
    public double askPrice;
    @JsonProperty("A")
    public double askSize;

    public SocketBookTicker() {
    }
}
