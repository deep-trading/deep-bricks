package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * AccountBalance V2
 */
public class AccountProfit {
    public final String asset;

    public final String account;

    @JsonProperty("last_size")
    private double lastSize;

    private double size;

    private double price;

    private long quantity;

    private long time;

    public AccountProfit(String asset, String account,
                         double lastSize, double size,
                         double price, long quantity) {
        this.asset = asset;
        this.account = account;
        this.lastSize = lastSize;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
        this.time = System.currentTimeMillis();
    }

    public AccountProfit(String asset, String account, double lastSize,
                         double size, double price, long quantity, long time) {
        this.asset = asset;
        this.account = account;
        this.lastSize = lastSize;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
        this.time = time;
    }

    public double getLastSize() {
        return lastSize;
    }

    public void setLastSize(double lastSize) {
        this.lastSize = lastSize;
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
        if (!(o instanceof AccountProfit)) return false;
        AccountProfit that = (AccountProfit) o;
        return Double.compare(that.lastSize, lastSize) == 0 &&
                Double.compare(that.size, size) == 0 &&
                Double.compare(that.price, price) == 0 &&
                quantity == that.quantity &&
                time == that.time &&
                asset.equals(that.asset) &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asset, account, lastSize, size, price, quantity, time);
    }

    @Override
    public String toString() {
        return "AccountProfit{" +
                "asset='" + asset + '\'' +
                ", account='" + account + '\'' +
                ", lastSize=" + lastSize +
                ", size=" + size +
                ", price=" + price +
                ", quantity=" + quantity +
                ", time=" + time +
                '}';
    }
}
