package org.eurekaka.bricks.exchange.gate;

class GateOrder {
    public String label;

    public long id;
    public String contract;
    public int size;
    public int left;
    public double price;
    public double fill_price;
    public String tif;
    public String text;
    public String status;
    public String finish_as;
    public long finish_time;
    public long create_time;

    public GateOrder() {
    }
}
