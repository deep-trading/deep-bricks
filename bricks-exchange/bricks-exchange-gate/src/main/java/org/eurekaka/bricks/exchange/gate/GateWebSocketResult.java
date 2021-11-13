package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class GateWebSocketResult {

    public String status;
    public String contract;
    public double mark_price;
    public double funding_rate;

    public String id;
    public String order_id;

    public double price;
    public long size;
    public String role;
    public long create_time_ms;
    public long finish_time_ms;

    public long time_ms;

    public double entry_price;

    public long left;

    public String mode;

    public List<GateWebSocketOrderBook> asks;
    public List<GateWebSocketOrderBook> bids;

    @JsonProperty("p")
    public double book_price;
    @JsonProperty("s")
    public long book_size;
    @JsonProperty("c")
    public String book_contract;

}
