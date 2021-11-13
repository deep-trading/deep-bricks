package org.eurekaka.bricks.exchange.binance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class BinanceSocketSub {
    public String method;
    public List<String> params;
    public long id;

    public BinanceSocketSub(String method, String... params) {
        this.method = method;
        this.params = new ArrayList<>();
        this.params.addAll(Arrays.asList(params));
        this.id = System.currentTimeMillis();
    }
}
