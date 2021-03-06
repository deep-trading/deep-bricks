package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AccountStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.zip.GZIPInputStream;

public abstract class WebSocketListener<A extends AccountStatus, B extends ExApi> implements WebSocket.Listener {
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());

    private final List<CharSequence> parts = new ArrayList<>();
    private final List<ByteBuffer> binaryParts;
    private CompletableFuture<?> accumulatedMessage = new CompletableFuture<>();

    protected AccountConfig accountConfig;
    protected A accountStatus;
    protected B api;

    private final Executor executor;

    protected int orderBookLimit;

    public WebSocketListener(AccountConfig accountConfig, A accountStatus, B api, Executor executor) {
        this.accountConfig = accountConfig;
        this.accountStatus = accountStatus;
        this.api = api;

        this.executor = executor;

        this.binaryParts = new ArrayList<>();

        this.orderBookLimit = Integer.parseInt(accountConfig.getProperty(
                "order_book_limit", "500"));
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        webSocket.request(1);
        parts.add(data);
        if (last) {
            StringBuilder text = new StringBuilder();
            for (CharSequence part : parts) {
                text.append(part);
            }
            long startTime = System.currentTimeMillis();
//            accumulatedMessage.completeAsync(() -> {
//                return null;
//            }, executor);
            String message = text.toString();
            try {
                processWholeText(webSocket, message);
            } catch (Throwable e) {
                logger.error("failed to process message: {}", text, e);
            }
            long timeCost = System.currentTimeMillis() - startTime;
            if (timeCost > 1) {
                logger.info("time cost: {}, message: {}", timeCost, message);
            }
            accumulatedMessage.complete(null);
            parts.clear();
            CompletionStage<?> cf = accumulatedMessage;
            accumulatedMessage = new CompletableFuture<>();
            return cf;
        }

        return accumulatedMessage;
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        webSocket.request(1);
        binaryParts.add(data);
        if (last) {
            int size = 0;
            for (ByteBuffer binaryPart : binaryParts) {
                size += binaryPart.array().length;
            }
            var allocate = ByteBuffer.allocate(size);
            for (ByteBuffer binaryPart : binaryParts) {
                allocate.put(binaryPart);
            }
            var content = uncompress(allocate.array());
            if (content != null) {
                var text = new String(content);

                try {
                    logger.trace("received message: {}", text);
                    processWholeText(webSocket, text);
                } catch (Throwable t) {
                    logger.error("failed to process message: {}", text, t);
                }
            } else {
                logger.warn("failed to uncompress data");
            }

            binaryParts.clear();
            accumulatedMessage.complete(null);
            CompletionStage<?> cf = accumulatedMessage;
            accumulatedMessage = new CompletableFuture<>();
            return cf;
        }

        return accumulatedMessage;
    }

    /**
     *     // spot??????????????????????????????????????????
     *     onOrder();
     *     onFilled();
     *     onDepthPrice();
     *     onKlineData();
     *
     *     // futures
     *     onPosition();
     *     onFundingRate();
     * @param message ?????????????????????
     */
    protected abstract void processWholeText(WebSocket webSocket, String message) throws Exception;

    protected byte[] uncompress(byte[] data) {
        try (ByteArrayInputStream is = new ByteArrayInputStream(data);
             ByteArrayOutputStream os = new ByteArrayOutputStream();
             GZIPInputStream gis = new GZIPInputStream(is)) {
            int count;
            byte[] buffer = new byte[1024];
            while ((count = gis.read(buffer, 0, 1024)) != -1) {
                os.write(buffer, 0, count);
            }
            os.flush();
            return os.toByteArray();
        } catch (IOException e) {
            logger.error("failed to uncompress data", e);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
        webSocket.request(1);
        accountStatus.updateLastPongTime();
        return null;
    }
}
