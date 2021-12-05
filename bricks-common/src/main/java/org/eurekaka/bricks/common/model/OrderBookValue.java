package org.eurekaka.bricks.common.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OrderBookValue {

    public final long lastUpdateId;
    public final long firstUpdateId;

    public final List<PriceSizePair> bids;
    public final List<PriceSizePair> asks;

    public static List<PriceSizePair> parsePairs(List<List<Double>> priceSizes) {
        List<PriceSizePair> pairs = new ArrayList<>();
        if (priceSizes != null) {
            for (List<Double> priceSize : priceSizes) {
                pairs.add(new PriceSizePair(priceSize.get(0), priceSize.get(1)));
            }
        }
        return pairs;
    }

    public OrderBookValue(long lastUpdateId, long firstUpdateId,
                          List<PriceSizePair> bids, List<PriceSizePair> asks) {
        this.lastUpdateId = lastUpdateId;
        this.firstUpdateId = firstUpdateId;
        this.bids = bids;
        this.asks = asks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderBookValue)) return false;
        OrderBookValue that = (OrderBookValue) o;
        return lastUpdateId == that.lastUpdateId &&
                firstUpdateId == that.firstUpdateId &&
                bids.equals(that.bids) && asks.equals(that.asks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastUpdateId, firstUpdateId, bids, asks);
    }

    @Override
    public String toString() {
        List<PriceSizePair> bidHeads = bids.subList(0, Math.min(bids.size(), 10));
        return "OrderBookValue{" +
                "lastUpdateId=" + lastUpdateId +
                ", firstUpdateId=" + firstUpdateId +
                ", bids=" + bids.size() + ", " + bids.subList(0, Math.min(bids.size(), 10)) +
                ", asks=" + asks.size() + ", " + asks.subList(0, Math.min(asks.size(), 10)) +
                '}';
    }

    public static class PriceSizePair {
        public final double price;
        public final double size;

        public PriceSizePair(double price, double size) {
            this.price = price;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PriceSizePair)) return false;
            PriceSizePair that = (PriceSizePair) o;
            return Double.compare(that.price, price) == 0 && Double.compare(that.size, size) == 0;
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

}
