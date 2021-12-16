package org.eurekaka.bricks.server.model;

import org.eurekaka.bricks.common.model.Order;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderType;

import java.util.Objects;

public class AsyncStateOrder extends Order {
    private OrderState state;

    public AsyncStateOrder(String account, String name, String symbol, OrderSide side,
                           OrderType orderType, double size, double price, long quantity, OrderState state) {
        super(account, name, symbol, side, orderType, size, price, quantity);
        this.state = state;
    }

    public AsyncStateOrder(String account, String name, String symbol, OrderSide side,
                           OrderType orderType, double size, double price, long quantity,
                           String clientOrderId, OrderState state) {
        super(account, name, symbol, side, orderType, size, price, quantity);
        this.setClientOrderId(clientOrderId);
        this.state = state;
    }

    public OrderState getState() {
        return state;
    }

    public synchronized void setState(OrderState state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AsyncStateOrder)) return false;
        if (!super.equals(o)) return false;
        AsyncStateOrder that = (AsyncStateOrder) o;
        return state == that.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), state);
    }

    @Override
    public String toString() {
        return "AsyncStateOrder{" +
                "state=" + state +
                "} " + super.toString();
    }
}
