package org.eurekaka.bricks.exchange.gate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GateWebSocketRequest {
    public long time;
    public String channel;
    public Map<String, String> auth;
    public String event;
    public List<String> payload;

    public GateWebSocketRequest(String channel,
                                String event, List<String> payload) {
        this.time = System.currentTimeMillis() / 1000;
        this.channel = channel;
        this.event = event;
        this.payload = payload;
    }
}
