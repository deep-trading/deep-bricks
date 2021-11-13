package org.eurekaka.bricks.exchange.huobi;

import java.util.List;

class HuoFutureWebSocketV2 {
    public String topic;
    public String op;

    public long ts;
    public String event;

    public String contract_code;
    public long volume;
    public double price;
    public String order_price_type;
    public String direction;
    public String offset;
    public int status;
    public String order_id_str;
    public int order_type;

    public List<HuoFutureTrade> trade;

    public List<HuoFutureDataV1> data;


    public HuoFutureWebSocketV2() {
    }
}
