package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class PlanOrder {
    private long id;
    private String name;
    private long quantity;
    private long symbolPrice;
    private long leftQuantity;
    private long startTime;
    private long updateTime;

    public PlanOrder(long id, String name, long quantity, long symbolPrice,
                       long leftQuantity, long startTime, long updateTime) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.symbolPrice = symbolPrice;
        this.leftQuantity = leftQuantity;
        this.startTime = startTime;
        this.updateTime = updateTime;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getSymbolPrice() {
        return symbolPrice;
    }

    public void setSymbolPrice(long symbolPrice) {
        this.symbolPrice = symbolPrice;
    }

    public long getLeftQuantity() {
        return leftQuantity;
    }

    public void setLeftQuantity(long leftQuantity) {
        this.leftQuantity = leftQuantity;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "PlanOrder{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", symbolPrice=" + symbolPrice +
                ", leftQuantity=" + leftQuantity +
                ", startTime=" + startTime +
                ", updateTime=" + updateTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlanOrder planOrder = (PlanOrder) o;
        return id == planOrder.id &&
                quantity == planOrder.quantity &&
                symbolPrice == planOrder.symbolPrice &&
                leftQuantity == planOrder.leftQuantity &&
                startTime == planOrder.startTime &&
                updateTime == planOrder.updateTime &&
                name.equals(planOrder.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, quantity, symbolPrice, leftQuantity, startTime, updateTime);
    }

}
