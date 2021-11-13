package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Info<T extends Info<T>> implements Copyable<T> {
    // 数据库存储id
    protected int id;

    // 系统内部使用名称
    protected String name;

    // 平台实际symbol名称
    protected String symbol;

    // 对应平台账号
    protected String account;

    // 价格精度
    @JsonProperty("price_precision")
    protected double pricePrecision;

    // 数量精度
    @JsonProperty("size_precision")
    protected double sizePrecision;

    // 确定是否enabled
    protected boolean enabled;

    public Info() {
    }

    public Info(int id, String name, String symbol, String account,
                double pricePrecision, double sizePrecision, boolean enabled) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.pricePrecision = pricePrecision;
        this.sizePrecision = sizePrecision;
        this.enabled = enabled;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getAccount() {
        return account;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getPricePrecision() {
        return pricePrecision;
    }

    public void setPricePrecision(double pricePrecision) {
        this.pricePrecision = pricePrecision;
    }

    public double getSizePrecision() {
        return sizePrecision;
    }

    public void setSizePrecision(double sizePrecision) {
        this.sizePrecision = sizePrecision;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    @Override
    public void copy(T other) {
        this.sizePrecision = other.getSizePrecision();
        this.pricePrecision = other.getPricePrecision();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Info)) return false;
        Info<?> info = (Info<?>) o;
        return id == info.id &&
                Double.compare(info.pricePrecision, pricePrecision) == 0 &&
                Double.compare(info.sizePrecision, sizePrecision) == 0 &&
                enabled == info.enabled &&
                name.equals(info.name) &&
                symbol.equals(info.symbol) &&
                account.equals(info.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, symbol, account, pricePrecision, sizePrecision, enabled);
    }

    @Override
    public String toString() {
        return "SymInfo{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", pricePrecision=" + pricePrecision +
                ", sizePrecision=" + sizePrecision +
                ", enabled=" + enabled +
                '}';
    }
}
