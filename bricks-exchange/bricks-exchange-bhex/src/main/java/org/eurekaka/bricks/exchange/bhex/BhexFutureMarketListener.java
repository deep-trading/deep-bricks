package org.eurekaka.bricks.exchange.bhex;

import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.FutureAccountStatus;

import java.net.http.WebSocket;
import java.util.concurrent.Executor;

public class BhexFutureMarketListener extends WebSocketListener<FutureAccountStatus, BhexFutureApi> {
    public BhexFutureMarketListener(AccountConfig accountConfig,
                                    FutureAccountStatus accountStatus,
                                    BhexFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {

    }
}
