package org.eurekaka.bricks.exchange.bhex;

import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.FutureAccountStatus;

import java.net.http.WebSocket;

public class BhexFutureMarketListener extends WebSocketListener<FutureAccountStatus, BhexFutureApi> {
    public BhexFutureMarketListener(AccountConfig accountConfig,
                                    FutureAccountStatus accountStatus,
                                    BhexFutureApi api) {
        super(accountConfig, accountStatus, api);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {

    }
}
