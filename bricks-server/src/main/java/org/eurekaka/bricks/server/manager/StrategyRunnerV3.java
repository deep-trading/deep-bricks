package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 策略定时执行器
 */
public class StrategyRunnerV3 implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(StrategyRunnerV3.class);

    private final AtomicBoolean exited;
    private final Strategy strategy;
    private final BlockingQueue<Notification> queue;
    private final NotificationListener listener;

    public StrategyRunnerV3(AtomicBoolean exited, Strategy strategy,
                            BlockingQueue<Notification> queue) {
        this(exited, strategy, queue, null);
    }

    public StrategyRunnerV3(AtomicBoolean exited, Strategy strategy,
                            BlockingQueue<Notification> queue, NotificationListener listener) {
        this.exited = exited;
        this.strategy = strategy;
        this.queue = queue;
        this.listener = listener;
    }

    @Override
    public void run() {
        while (!exited.get()) {
            try {
                // todo:: add metrics for orders
                Notification notification = queue.take();
                if (listener != null) {
                    listener.onNotification(notification);
                }
                strategy.notify(notification);
            } catch (InterruptedException e) {
                exited.set(true);
//                logger.info("notification runner interrupted.");
            } catch (Exception e) {
                logger.error("notification runner error", e);
            }
        }

        if (queue.size() != 0) {
            for (Notification notification : queue) {
                try {
                    if (listener != null) {
                        listener.onNotification(notification);
                    }
                    strategy.notify(notification);
                } catch (Exception e) {
                    logger.error("failed to process notification: {} when existing", notification, e);
                }
            }
        }
    }
}
