package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

class GatePriceSizePair {
    @JsonProperty("p")
    public double price;

    @JsonProperty("s")
    public int size;

    public GatePriceSizePair() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GatePriceSizePair)) return false;
        GatePriceSizePair that = (GatePriceSizePair) o;
        return Double.compare(that.price, price) == 0 && size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(price, size);
    }

    @Override
    public String toString() {
        return "(" + price + ", " + size + ')';
    }
}
