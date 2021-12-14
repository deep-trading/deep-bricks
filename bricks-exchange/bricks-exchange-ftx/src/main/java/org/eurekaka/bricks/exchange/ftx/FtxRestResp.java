package org.eurekaka.bricks.exchange.ftx;

import com.fasterxml.jackson.databind.JsonNode;

class FtxRestResp {
    public boolean success;
    public String error;

    public JsonNode result;

    public FtxRestResp() {
    }
}
