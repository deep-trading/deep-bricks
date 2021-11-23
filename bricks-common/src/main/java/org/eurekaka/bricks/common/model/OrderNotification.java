package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class OrderNotification implements Notification {
    // order id
    private final String id;

    private final String name;
    private final String symbol;
    private final String account;

    private final OrderSide side;
    private final OrderType type;

    private final double size;
    private final double price;

    // 必须有，若为market order，未成交前0，成交后size
    private final double filledSize;
    private final double avgPrice;

    private final String clientOrderId;
    private final OrderStatus status;
    private final long time;

    public OrderNotification(String id, String name, String symbol, String account,
                             OrderSide side, OrderType type, double size, double price, double filledSize) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;

        this.avgPrice = price;
        this.clientOrderId = null;
        this.status = OrderStatus.NIL;
        this.time = System.currentTimeMillis();
    }

    public OrderNotification(String id, String name, String symbol, String account,
                             OrderSide side, OrderType type, double size, double price,
                             double filledSize, double avgPrice,
                             String clientOrderId, OrderStatus status, long time) {
        this.id = id;
        this.name = name;
        this.symbol = symbol;
        this.account = account;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;
        this.avgPrice = avgPrice;
        this.clientOrderId = clientOrderId;
        this.status = status;
        this.time = time;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAccount() {
        return account;
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

    public OrderStatus getStatus() {
        return status;
//        if (filledSize == size && size > 0) {
//            return OrderStatus.FILLED.name();
//        } else if (filledSize == 0) {
//            return OrderStatus.NEW.name();
//        } else {
//            return OrderStatus.PART_FILLED.name();
//        }
    }

    public long getTime() {
        return time;
    }

    public double getAvgPrice() {
        return avgPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderNotification)) return false;
        OrderNotification that = (OrderNotification) o;
        return Double.compare(that.size, size) == 0 &&
                Double.compare(that.price, price) == 0 &&
                Double.compare(that.filledSize, filledSize) == 0 &&
                Double.compare(that.avgPrice, avgPrice) == 0 &&
                time == that.time && id.equals(that.id) &&
                Objects.equals(name, that.name) && symbol.equals(that.symbol) &&
                account.equals(that.account) && side == that.side &&
                type == that.type && Objects.equals(clientOrderId, that.clientOrderId) &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, symbol, account, side, type, size, price,
                filledSize, avgPrice, clientOrderId, status, time);
    }

    @Override
    public String toString() {
        return "OrderNotification{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", side=" + side +
                ", type=" + type +
                ", size=" + size +
                ", price=" + price +
                ", filledSize=" + filledSize +
                ", avgPrice=" + avgPrice +
                ", clientOrderId='" + clientOrderId + '\'' +
                ", status=" + status +
                ", time=" + time +
                '}';
    }
}
