package org.eurekaka.bricks.exchange.binance;

import java.util.List;

class BinanceRestV1 {
    public int code;
    public String msg;

    public List<BinanceSymbolInfo> symbols;

    public List<BinanceAssetData> assets;

    public List<BinancePositionData> positions;
    public double totalWalletBalance;
    public double availableBalance;

    public String orderId;

    public String listenKey;

    public String tranId;
    public String id;

    public String clientOrderId;
    public double executedQty;
    public double origQty;
    public double price;
    public String side;
    public String symbol;
    public String timeInForce;
    public String type;
    public String status;
    public long updateTime;

    public long lastUpdateId;
    public List<List<Double>> bids;
    public List<List<Double>> asks;

    public BinanceRestV1() {
    }
}
