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

    public BinanceRestV1() {
    }
}
