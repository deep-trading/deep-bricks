package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class FundingValue {

    private String name;

    private String symbol;
    private String account;
    // 正数代表账户收入，负数代表支出
    private double value;
    private double rate;
    private long time;

    public FundingValue(String symbol, String account, double value, double rate, long time) {
        this(null, symbol, account, value, rate, time);
    }

    public FundingValue(String name, String symbol, String account, double value, double rate, long time) {
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.value = value;
        this.rate = rate;
        this.time = time;
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

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
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
        if (!(o instanceof FundingValue)) return false;
        FundingValue that = (FundingValue) o;
        return Double.compare(that.value, value) == 0 &&
                Double.compare(that.rate, rate) == 0 &&
                time == that.time &&
                name.equals(that.name) &&
                symbol.equals(that.symbol) &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, symbol, account, value, rate, time);
    }

    @Override
    public String toString() {
        return "FundingValue{" +
                "name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", exchange='" + account + '\'' +
                ", value=" + value +
                ", rate=" + rate +
                ", time=" + time +
                '}';
    }
}
