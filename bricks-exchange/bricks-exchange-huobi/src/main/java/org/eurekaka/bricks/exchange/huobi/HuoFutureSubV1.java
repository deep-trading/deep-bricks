package org.eurekaka.bricks.exchange.huobi;

class HuoFutureSubV1 {

    public String sub;
    public String id;

    public HuoFutureSubV1(String sub) {
        this.sub = sub;
        this.id = String.valueOf(System.nanoTime());
    }


}
