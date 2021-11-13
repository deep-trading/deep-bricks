package org.eurekaka.bricks.common.model;

import java.util.Objects;

/**
 * AccountValue - AccountBaseValue,
 * 也可以只记录当前account value状态,
 * account value * net value = account balance
 */
public class AccountBalance {
    public final String asset;

    public final String account;

    private double size;

    private double price;

    private long quantity;

    private long time;

    public AccountBalance(String asset, String account,
                          double size, double price, long quantity) {
        this(asset, account, size, price, quantity, System.currentTimeMillis());
    }

    public AccountBalance(String asset, String account,
                          double size, double price,
                          long quantity, long time) {
        this.asset = asset;
        this.account = account;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
        this.time = time;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountBalance)) return false;
        AccountBalance that = (AccountBalance) o;
        return Double.compare(that.size, size) == 0 &&
                Double.compare(that.price, price) == 0 &&
                quantity == that.quantity &&
                time == that.time &&
                asset.equals(that.asset) &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asset, account, size, price, quantity, time);
    }

    @Override
    public String toString() {
        return "AccountBalance{" +
                "asset='" + asset + '\'' +
                ", account='" + account + '\'' +
                ", size=" + size +
                ", price=" + price +
                ", quantity=" + quantity +
                ", time=" + time +
                '}';
    }
}
