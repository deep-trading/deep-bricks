package org.eurekaka.bricks.exchange.bhex;


class BhexPosValue {
    public final String symbol;
    public final String side;
    public final double size;
    public final double availSize;
    public final double lastPrice;
    public final long positionValue;
    public final long margin;

    public BhexPosValue(String symbol, String side, double size, double availSize,
                        double lastPrice, long positionValue, long margin) {
        this.symbol = symbol;
        this.side = side;
        this.size = size;
        this.availSize = availSize;
        this.lastPrice = lastPrice;
        this.positionValue = positionValue;
        this.margin = margin;
    }

    @Override
    public String toString() {
        return "BhexPosValue{" +
                "symbol='" + symbol + '\'' +
                ", side='" + side + '\'' +
                ", size=" + size +
                ", positionValue=" + positionValue +
                ", margin=" + margin +
                '}';
    }
}
