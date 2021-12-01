package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class CurrentOrder {
    // order id
    private final String id;

    private final String symbol;

    private final OrderSide side;
    private final OrderType type;

    private final double size;
    private final double price;

    // 必须有，若为market order，未成交前0，成交后size
    @JsonProperty("filled_size")
    private double filledSize;

    private String name;

    private final long time;
    private final OrderStatus status;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    private String account;

    public CurrentOrder(String id, String name, String symbol, OrderSide side, OrderType type,
                        double size, double price, double filledSize) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;
        this.status = OrderStatus.NIL;
        this.time = System.currentTimeMillis();
    }

    public CurrentOrder(String id, String name, String symbol, String account,
                        OrderSide side, OrderType type, double size, double price,
                        double filledSize, OrderStatus status, long time, String clientOrderId) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;
        this.name = name;
        this.status = status;
        this.time = time;
        this.clientOrderId = clientOrderId;
        this.account = account;
    }

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getType() {
        return type;
    }

    public double getSize() {
        return size;
    }

    public double getPrice() {
        return price;
    }

    public double getFilledSize() {
        return filledSize;
    }

    public void setFilledSize(double filledSize) {
        this.filledSize = filledSize;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTime() {
        return time;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public String getAccount() {
        return account;
    }

    @Override
    public String toString() {
        return "CurrentOrder{" +
                "id='" + id + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", type=" + type +
                ", size=" + size +
                ", price=" + price +
                ", filledSize=" + filledSize +
                ", name='" + name + '\'' +
                ", account='" + account + '\'' +
                ", time=" + time +
                ", status=" + status +
                ", clientOrderId='" + clientOrderId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurrentOrder)) return false;
        CurrentOrder that = (CurrentOrder) o;
        return Double.compare(that.size, size) == 0 &&
                Double.compare(that.price, price) == 0 &&
                Double.compare(that.filledSize, filledSize) == 0 &&
                time == that.time && id.equals(that.id) &&
                symbol.equals(that.symbol) && side == that.side &&
                type == that.type && Objects.equals(name, that.name) &&
                Objects.equals(account, that.account) &&
                status == that.status &&
                Objects.equals(clientOrderId, that.clientOrderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, symbol, side, type, size, price,
                filledSize, name, time, status, clientOrderId, account);
    }
}
