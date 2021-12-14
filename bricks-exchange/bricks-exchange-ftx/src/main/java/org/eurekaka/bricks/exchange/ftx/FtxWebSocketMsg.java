package org.eurekaka.bricks.exchange.ftx;

class FtxWebSocketMsg {
    public String op;
    public String channel;
    public String market;
    public String type;
    public FtxWebSocketData data;

    public FtxWebSocketMsg() {
    }

    public FtxWebSocketMsg(String op, String channel) {
        this.op = op;
        this.channel = channel;
    }

    public FtxWebSocketMsg(String op, String channel, String market) {
        this.op = op;
        this.channel = channel;
        this.market = market;
    }
}
