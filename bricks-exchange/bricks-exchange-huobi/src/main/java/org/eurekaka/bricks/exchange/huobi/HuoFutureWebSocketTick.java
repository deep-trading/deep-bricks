package org.eurekaka.bricks.exchange.huobi;

import java.util.List;

public class HuoFutureWebSocketTick {
    public double index_price;
    public double contract_price;
    public double basis;
    public double basis_rate;

    public List<List<Double>> asks;
    public List<List<Double>> bids;

    public HuoFutureWebSocketTick() {
    }

}
