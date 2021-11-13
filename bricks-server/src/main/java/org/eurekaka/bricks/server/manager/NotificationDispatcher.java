package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.common.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NotificationDispatcher implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final AtomicBoolean exited;
    private final Map<String, BlockingQueue<Notification>> queueMap;
    private final BlockingQueue<Notification> blockingQueue;
    private final NotificationListener listener;

    public NotificationDispatcher(AtomicBoolean exited,
                                  Map<String, BlockingQueue<Notification>> queueMap,
                                  BlockingQueue<Notification> blockingQueue,
                                  NotificationListener listener) {
        this.exited = exited;
        this.queueMap = queueMap;
        this.blockingQueue = blockingQueue;
        this.listener = listener;
    }

    @Override
    public void run() {
        while (!exited.get()) {
            try {
                // todo:: add metrics for orders
                Notification notification = blockingQueue.take();
                // 统一监听处理所有消息通知
                listener.onNotification(notification);
                BlockingQueue<Notification> queue = queueMap.get(notification.getName());
                if (queue != null) {
                    queue.add(notification);
                }
//                else {
//                    logger.warn("found unknown notification: {}", notification);
//                }
            } catch (InterruptedException e) {
                exited.set(true);
//                logger.info("notification processor interrupted.");
            } catch (Exception e) {
                logger.error("notification processor error", e);
            }
        }

        if (blockingQueue.size() != 0) {
            for (Notification notification : blockingQueue) {
                try {
                    BlockingQueue<Notification> queue = queueMap.get(notification.getName());
                    if (queue != null) {
                        queue.add(notification);
                    }
                } catch (Exception e) {
                    logger.error("failed to process notification: {} when existing", notification, e);
                }
            }
        }
    }
}
