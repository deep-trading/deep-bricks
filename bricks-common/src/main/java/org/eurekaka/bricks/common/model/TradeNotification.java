package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class TradeNotification implements Notification {

    // 数据库递增id
    private int id;

    private String fillId;
    private String orderId;
    private String account;
    private String name;
    private String symbol;
    private OrderSide side;
    private OrderType type;
    private double price;
    private double size;
    private double result;
    private String feeAsset;
    private double fee;
    private long time;

    public TradeNotification(String fillId, String orderId, String account,
                             String name, String symbol, OrderSide side, OrderType type,
                             double price, double size, double result,
                             String feeAsset, double fee, long time) {
        this.fillId = fillId;
        this.orderId = orderId;
        this.account = account;
        this.name = name;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.size = size;
        this.result = result;
        this.feeAsset = feeAsset;
        this.fee = fee;
        this.time = time;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFillId() {
        return fillId;
    }

    public void setFillId(String fillId) {
        this.fillId = fillId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    @Override
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

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getSize() {
        return size;
    }

    public void setSize(double size) {
        this.size = size;
    }

    public double getResult() {
        return result;
    }

    public void setResult(double result) {
        this.result = result;
    }

    public String getFeeAsset() {
        return feeAsset;
    }

    public void setFeeAsset(String feeAsset) {
        this.feeAsset = feeAsset;
    }

    public double getFee() {
        return fee;
    }

    public void setFee(double fee) {
        this.fee = fee;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "TradeNotification{" +
                "fillId='" + fillId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", account='" + account + '\'' +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", type=" + type +
                ", price=" + price +
                ", size=" + size +
                ", result=" + result +
                ", feeAsset='" + feeAsset + '\'' +
                ", fee=" + fee +
                ", time=" + time +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TradeNotification)) return false;
        TradeNotification that = (TradeNotification) o;
        return id == that.id && Double.compare(that.price, price) == 0 &&
                Double.compare(that.size, size) == 0 &&
                Double.compare(that.result, result) == 0 &&
                Double.compare(that.fee, fee) == 0 &&
                time == that.time && fillId.equals(that.fillId) &&
                orderId.equals(that.orderId) && account.equals(that.account) &&
                name.equals(that.name) && symbol.equals(that.symbol) &&
                side == that.side && type == that.type && feeAsset.equals(that.feeAsset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fillId, orderId, account, name, symbol,
                side, type, price, size, result, feeAsset, fee, time);
    }
}
