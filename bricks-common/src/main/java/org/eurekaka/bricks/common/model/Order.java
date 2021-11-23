package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class Order {
    // self generated order id by database
    private long id;

    // 交易订单所属的账户
    private final String account;

    // 系统内部统一定义交易对名称
    // inner system united symbol
    private final String name;

    // 目标交易对名称，不一定需要
    // target platform symbol
    private final String symbol;

    // 订单方向
    private final OrderSide side;
    // 订单类型
    private final OrderType orderType;

    // 美金数量, dollars
    private final long quantity;
    // order asset size
    private final double size;
    // order price, required for limit order
    private final double price;

    // 下单后目标平台返回的订单id
    private String orderId;
    // 添加client order id
    private String clientOrderId;

    public Order(String account, String name, String symbol, OrderSide side,
                 OrderType orderType, double size, double price, long quantity) {
        this.account = account;
        this.name = name;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.size = size;
        this.price = price;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public long getQuantity() {
        return quantity;
    }

    public double getSize() {
        return size;
    }

    public double getPrice() {
        return price;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getAccount() {
        return account;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order)) return false;
        Order order = (Order) o;
        return id == order.id &&
                quantity == order.quantity &&
                Double.compare(order.size, size) == 0 &&
                Double.compare(order.price, price) == 0 &&
                name.equals(order.name) &&
                Objects.equals(symbol, order.symbol) &&
                side == order.side &&
                orderType == order.orderType &&
                Objects.equals(orderId, order.orderId) &&
                Objects.equals(clientOrderId, order.clientOrderId) &&
                Objects.equals(account, order.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, symbol, side, orderType, quantity, size, price, orderId, account, clientOrderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", orderType=" + orderType +
                ", quantity=" + quantity +
                ", size=" + size +
                ", price=" + price +
                ", orderId='" + orderId + '\'' +
                ", clientOrderId='" + clientOrderId + '\'' +
                ", account='" + account + '\'' +
                '}';
    }
}
