package org.eurekaka.bricks.exchange.gate;

import java.util.List;

class GateWebSocketResponse {
    public long time;
    public String channel;
    public String event;
    public Object error;
    public List<GateWebSocketResult> result;

    public GateWebSocketResponse() {
    }
}
