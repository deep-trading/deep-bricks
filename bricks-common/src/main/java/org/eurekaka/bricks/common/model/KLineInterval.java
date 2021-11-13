package org.eurekaka.bricks.common.model;

public enum KLineInterval {
    _1M("1m", 60),
    _3M("3m", 180),
    _5M("5m", 300),
    _15M("15m", 900),
    _30M("30m", 1800),
    _1H("1h", 3600),
    _2H("2h", 7200),
    _4H("4h", 14400),
    _6H("6h", 21600),
    _8H("8h", 28800),
    _12H("12h", 43200),
    _1D("1d", 86400),
    ;

    public final String value;
    public final int interval;

    KLineInterval(String value, int interval) {
        this.value = value;
        this.interval = interval;
    }

    public static KLineInterval getKLineInterval(String name) {
        for (KLineInterval v : values()) {
            if (v.value.equalsIgnoreCase(name)) {
                return v;
            }
        }
        throw new IllegalArgumentException("failed to get kline interval: " + name);
    }
}
