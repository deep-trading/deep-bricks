package org.eurekaka.bricks.exchange.huobi;

class HuoFuturePongV2 {
    public String op;

    public long ts;

    public HuoFuturePongV2(long ts) {
        this.op = "pong";
        this.ts = ts;
    }
}
