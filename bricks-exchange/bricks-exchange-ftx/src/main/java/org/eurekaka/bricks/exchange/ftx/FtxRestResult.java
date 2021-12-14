package org.eurekaka.bricks.exchange.ftx;

import java.util.List;

class FtxRestResult {

    // balance
    public String coin;
    public double free;
    public double total;

    // account
    public double collateral;
    public double freeCollateral;
    public double marginFraction;
    public double totalAccountValue;
    public int leverage;
    public List<FtxFutureApi.FtxRestResultPos> positions;

    public String name;
    public double priceIncrement;
    public double sizeIncrement;

    public String id;
    public String clientId;
    public String status;

    public String future;
    public double netSize;
    public double openSize;
    public double collateralUsed;
    public double initialMarginRequirement;
    public double maintenanceMarginRequirement;

    // 实际为标记价格
    public double entryPrice;
    public double recentBreakEvenPrice;
    public double recentPnl;

    public String side;
    public String type;
    public boolean ioc;
    public boolean postOnly;
    public double price;
    public double avgFillPrice;
    public double filledSize;
    public double size;
    public String createdAt;

    public double rate;
    public double payment;
    public String time;

    public double nextFundingRate;

    public String address;

    public FtxRestResult() {
    }


}
