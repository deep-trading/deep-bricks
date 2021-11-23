package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class GateWebSocketResult {

    public String status;
    public String finish_as;
    public String contract;
    public double mark_price;
    public double funding_rate;

    public String id;
    public String text;
    public String order_id;

    public double price;
    public double fill_price;
    public long size;
    public String role;
    public String tif;
    public long create_time_ms;
    public long finish_time_ms;

    public double mkfr;
    public double tkfr;

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
