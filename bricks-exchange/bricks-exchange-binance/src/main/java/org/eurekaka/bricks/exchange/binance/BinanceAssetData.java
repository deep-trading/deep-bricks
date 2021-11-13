package org.eurekaka.bricks.exchange.binance;

class BinanceAssetData {
    public String asset;
    public double walletBalance;
    public double unrealizedProfit;
    public double availableBalance;

    public String symbol;
    // 注意正数代表收入
    public double income;
    public double rate;
    public long time;

    public double fundingRate;

    public String coin;
    public double amount;
    public String applyTime;
    public String address;
    public int status;
    public long insertTime;

    public BinanceAssetData() {
    }
}
