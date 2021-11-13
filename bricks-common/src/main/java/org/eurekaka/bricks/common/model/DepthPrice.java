package org.eurekaka.bricks.common.model;

public class DepthPrice {

    public final String name;
    public final String symbol;

    // 最小深度要求的价格
    public final double price;
    // 实际深度，此处作为下单金额
    public final int realQty;
    // 实际深度size
    public final double realSize;

    public DepthPrice(String name, String symbol, double price, int realQty, double realSize) {
        this.name = name;
        this.symbol = symbol;
        this.price = price;
        this.realQty = realQty;
        this.realSize = realSize;
    }

    @Override
    public String toString() {
        return "DepthPrice{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", realQty=" + realQty +
                ", realSize=" + realSize +
                '}';
    }
}
