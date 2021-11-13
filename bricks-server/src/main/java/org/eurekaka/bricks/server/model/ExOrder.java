package org.eurekaka.bricks.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eurekaka.bricks.common.model.Order;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderType;

import java.util.Objects;

public class ExOrder extends Order {
    // 聚合下单时比单计算得到的价格
    @JsonProperty("last_price")
    private final double lastPrice;

    @JsonProperty("plan_id")
    private final long planId;

    public ExOrder(String account, String name, String symbol, OrderSide side, OrderType type,
                   double size, double price, long quantity, double lastPrice, long planId) {
        super(account, name, symbol, side, type, size, price, quantity);
        this.lastPrice = lastPrice;
        this.planId = planId;
    }

    public double getLastPrice() {
        return lastPrice;
    }

    public long getPlanId() {
        return planId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExOrder)) return false;
        if (!super.equals(o)) return false;
        ExOrder exOrder = (ExOrder) o;
        return Double.compare(exOrder.lastPrice, lastPrice) == 0 &&
                planId == exOrder.planId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lastPrice, planId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + getId() +
                ", account='" + getAccount() + '\'' +
                ", name='" + getName() + '\'' +
                ", symbol='" + getSymbol() + '\'' +
                ", side=" + getSide() +
                ", type=" + getOrderType() +
                ", quantity=" + getQuantity() +
                ", size=" + getSize() +
                ", price=" + getPrice() +
                ", orderId='" + getOrderId() + '\'' +
                ", last_price=" + getLastPrice() +
                ", plan_id=" + getPlanId() +
                '}';
    }
}
