package org.eurekaka.bricks.common.model;

/**
 * 平台上获取到的symbol信息
 */
public class ExSymbol {

    private String name;
    public final String symbol;

    public final double pricePrecision;
    public final double sizePrecision;

    public ExSymbol(String symbol, double pricePrecision, double sizePrecision) {
        this.symbol = symbol;
        this.pricePrecision = pricePrecision;
        this.sizePrecision = sizePrecision;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "ExSymbol{" +
                "symbol='" + symbol + '\'' +
                ", pricePrecision=" + pricePrecision +
                ", sizePrecision=" + sizePrecision +
                '}';
    }
}
