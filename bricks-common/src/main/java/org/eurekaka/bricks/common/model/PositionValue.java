package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class PositionValue {

    private String name;

    private String symbol;

    private String account;

    private double size;

    private double price;

    private long quantity;

    @JsonProperty("entry_price")
    private double entryPrice;

    @JsonProperty("unrealized_pnl")
    private double unPnl;

    private long time;

    public PositionValue(String symbol, String account,
                         double size, double price, long quantity,
                         double entryPrice, double unPnl) {
        this.symbol = symbol;
        this.account = account;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.unPnl = unPnl;
        this.time = System.currentTimeMillis();
    }

//    public PositionValue(String symbol, String account, double size, double price, long quantity) {
//        this(null, symbol, account, size, price, quantity, System.currentTimeMillis());
//    }
//
//    public PositionValue(String name, String symbol, String account,
//                         double size, double price, long quantity, long time) {
//        this.name = name;
//        this.symbol = symbol;
//        this.account = account;
//        this.size = size;
//        this.price = price;
//        this.quantity = quantity;
//        this.time = time;
//    }

    public PositionValue(String name, String symbol, String account,
                         double size, double price, long quantity,
                         double entryPrice, double unPnl, long time) {
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.size = size;
        this.price = price;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.unPnl = unPnl;
        this.time = time;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccount() {
        return account;
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

    public double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public double getUnPnl() {
        return unPnl;
    }

    public void setUnPnl(double unPnl) {
        this.unPnl = unPnl;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void merge(PositionValue other) {
        this.quantity = this.quantity + other.quantity;
        this.size = this.size + other.size;
    }

    public PositionValue copy() {
        return new PositionValue(name, symbol, account, size, price, quantity, entryPrice, unPnl, time);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PositionValue)) return false;
        PositionValue that = (PositionValue) o;
        return Double.compare(that.size, size) == 0 &&
                Double.compare(that.price, price) == 0 &&
                quantity == that.quantity &&
                Double.compare(that.entryPrice, entryPrice) == 0 &&
                Double.compare(that.unPnl, unPnl) == 0 &&
                time == that.time &&
                name.equals(that.name) &&
                symbol.equals(that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, account, size, price, quantity, entryPrice, unPnl, time);
    }

    @Override
    public String toString() {
        return "PositionValue{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", size=" + size +
                ", price=" + price +
                ", quantity=" + quantity +
                ", entryPrice=" + entryPrice +
                ", unPnl=" + unPnl +
                ", time=" + time +
                '}';
    }
}
