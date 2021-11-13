package org.eurekaka.bricks.exchange.huobi;

class HuoFutureSubV2 {
    public String op;
    public String cid;
    public String topic;

    public HuoFutureSubV2(String op, String topic) {
        this.op = op;
        this.cid = String.valueOf(System.nanoTime());
        this.topic = topic;
    }
}
