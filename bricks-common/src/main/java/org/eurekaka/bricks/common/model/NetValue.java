package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class NetValue {

    private String name;

    private String symbol;
    private String account;

    private long price;

    public NetValue(String symbol, String account, long price) {
        this.symbol = symbol;
        this.account = account;
        this.price = price;
    }

    public NetValue(String name, String symbol, String account, long price) {
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetValue)) return false;
        NetValue value = (NetValue) o;
        return price == value.price &&
                name.equals(value.name) &&
                symbol.equals(value.symbol) &&
                account.equals(value.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, account, price);
    }

    @Override
    public String toString() {
        return "NetValue{" +
                "name='" + name + '\'' +
                ", account='" + account + '\'' +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                '}';
    }
}
