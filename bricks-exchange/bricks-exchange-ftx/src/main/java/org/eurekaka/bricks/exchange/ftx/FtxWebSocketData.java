package org.eurekaka.bricks.exchange.ftx;

import java.util.List;

public class FtxWebSocketData {
    public String id;
    public String orderId;
    public String future;
    public double price;
    public String side;
    public double size;
    public double fee;
    public String liquidity;
    public String market;
    public String type;
    public double avgFillPrice;
    public double filledSize;

    public double bid;
    public double ask;
    public double last;

    public String action;
    public List<List<Double>> asks;
    public List<List<Double>> bids;

    public FtxWebSocketData() {
    }

}
