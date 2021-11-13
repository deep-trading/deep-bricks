package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    public CurrentOrder(String id, String symbol, OrderSide side, OrderType type,
                        double size, double price, double filledSize) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;
    }

    public CurrentOrder(String id, String name, String symbol, OrderSide side,
                        OrderType type, double size, double price, double filledSize) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.size = size;
        this.price = price;
        this.filledSize = filledSize;
        this.name = name;
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

    public String getStatus() {
        if (filledSize == size && size > 0) {
            return OrderStatus.FILLED.name();
        } else if (filledSize == 0) {
            return OrderStatus.CANCELLED.name();
        } else {
            return OrderStatus.PART_FILLED.name();
        }
    }

    @Override
    public String toString() {
        return "CurrentOrder{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", type=" + type +
                ", size=" + size +
                ", price=" + price +
                ", filledSize=" + filledSize +
                '}';
    }
}
