package org.eurekaka.bricks.exchange.bhex;

import com.fasterxml.jackson.core.type.TypeReference;
import org.eurekaka.bricks.api.WebSocketListener;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.FutureAccountStatus;
import org.eurekaka.bricks.common.model.PositionValue;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.WebSocket;
import java.util.List;
import java.util.concurrent.Executor;

public class BhexFutureListener extends WebSocketListener<FutureAccountStatus, BhexFutureApi> {
    public BhexFutureListener(AccountConfig accountConfig,
                              FutureAccountStatus accountStatus, BhexFutureApi api, Executor executor) {
        super(accountConfig, accountStatus, api, executor);
    }

    @Override
    protected void processWholeText(WebSocket webSocket, String message) throws Exception {
        if (message.startsWith("{")) {
            BhexWebsocketMsg msg = Utils.mapper.readValue(message, BhexWebsocketMsg.class);
            if (msg.event == null && msg.pong > 0) {
                accountStatus.updateLastPongTime();
            }
        } else if (message.startsWith("[")) {
            List<BhexWebsocketMsg> msgs = Utils.mapper.readValue(message, new TypeReference<>() {});
            for (BhexWebsocketMsg msg : msgs) {
                if ("outboundContractPositionInfo".equals(msg.event)) {
                    double size = api.getSize(msg.symbol, msg.availSize);
                    accountStatus.getPositionValues().put(msg.symbol + msg.direction,
                            new PositionValue(msg.symbol, accountConfig.getName(), size,
                                    0, 0, 0, 0));
                }
            }
        }
    }

}
