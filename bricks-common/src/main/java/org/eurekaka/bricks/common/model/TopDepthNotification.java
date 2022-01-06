package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class TopDepthNotification implements Notification {
    private final String name;
    private final String symbol;
    private final String account;
    private final DepthSide side;
    private final double topPrice;

    public TopDepthNotification(String name, String symbol, String account, DepthSide side, double topPrice) {
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.side = side;
        this.topPrice = topPrice;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAccount() {
        return account;
    }

    public String getSymbol() {
        return symbol;
    }

    public DepthSide getSide() {
        return side;
    }

    public double getTopPrice() {
        return topPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopDepthNotification)) return false;
        TopDepthNotification that = (TopDepthNotification) o;
        return Double.compare(that.topPrice, topPrice) == 0 &&
                name.equals(that.name) &&
                symbol.equals(that.symbol) &&
                account.equals(that.account) &&
                side == that.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, account, side, topPrice);
    }

    @Override
    public String toString() {
        return "DepthPriceNotification{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", side='" + side + '\'' +
                ", topPrice=" + topPrice +
                '}';
    }

    public enum DepthSide {
        BID,
        ASK,
    }
}
