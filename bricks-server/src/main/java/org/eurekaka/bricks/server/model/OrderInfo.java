package org.eurekaka.bricks.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.Info;

import java.util.Objects;

public class OrderInfo extends Info<OrderInfo> {

    // 有效深度美金数量
    @JsonProperty("depth_qty")
    private int depthQty;

    // 允许下单的方向
    private OrderSide side;

    public OrderInfo() {
    }

    public OrderInfo(int id, String name, String symbol, String account,
                     double pricePrecision, double sizePrecision,
                     int depthQty, OrderSide side, boolean enabled) {
        super(id, name, symbol, account, pricePrecision, sizePrecision, enabled);
        this.depthQty = depthQty;
        this.side = side;
    }

    public int getDepthQty() {
        return depthQty;
    }

    public void setDepthQty(int depthQty) {
        this.depthQty = depthQty;
    }

    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }

    public boolean buyAllowed() {
        return OrderSide.ALL.equals(side) || OrderSide.BUY.equals(side);
    }

    public boolean sellAllowed() {
        return OrderSide.ALL.equals(side) || OrderSide.SELL.equals(side);
    }

    @Override
    public void copy(OrderInfo other) {
        super.copy(other);
        this.depthQty = other.getDepthQty();
        this.side = other.getSide();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderInfo)) return false;
        if (!super.equals(o)) return false;
        OrderInfo orderInfo = (OrderInfo) o;
        return depthQty == orderInfo.depthQty &&
                side == orderInfo.side;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), depthQty, side);
    }

    @Override
    public String toString() {
        return "OrderSymInfo{" +
                "depthQty=" + depthQty +
                ", side=" + side +
                ", id=" + id +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", pricePrecision=" + pricePrecision +
                ", sizePrecision=" + sizePrecision +
                ", enabled=" + enabled +
                "} " + super.toString();
    }
}
