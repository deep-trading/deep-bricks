package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class TopDepthNotification implements Notification {
    private final String name;
    private final String symbol;
    private final String account;
    private final DepthSide side;

    public TopDepthNotification(String name, String symbol, String account, DepthSide side) {
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.side = side;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TopDepthNotification)) return false;
        TopDepthNotification that = (TopDepthNotification) o;
        return side.equals(that.side) &&
                name.equals(that.name) &&
                symbol.equals(that.symbol) &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, account, side);
    }

    @Override
    public String toString() {
        return "DepthPriceNotification{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", side='" + side + '\'' +
                '}';
    }

    public enum DepthSide {
        BID,
        ASK,
    }
}
