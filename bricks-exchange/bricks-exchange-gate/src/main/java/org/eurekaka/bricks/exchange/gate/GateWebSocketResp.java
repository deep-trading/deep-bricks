package org.eurekaka.bricks.exchange.gate;

import com.fasterxml.jackson.databind.JsonNode;

public class GateWebSocketResp {

    public long time;
    public String channel;
    public String event;
    public Object error;

    public JsonNode result;

    public GateWebSocketResp() {
    }
}
