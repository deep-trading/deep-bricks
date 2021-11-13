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
public class StrategyRunnerV2 implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(StrategyRunnerV2.class);

    private final AtomicBoolean exited;
    private final Strategy strategy;
    private final BlockingQueue<Notification> queue;
    private final NotificationListener listener;
    private final int interval;

    public StrategyRunnerV2(AtomicBoolean exited, Strategy strategy, BlockingQueue<Notification> queue) {
        this(exited, strategy, queue, 1000);
    }

    public StrategyRunnerV2(AtomicBoolean exited, Strategy strategy,
                            BlockingQueue<Notification> queue, int interval) {
        this(exited, strategy, queue, interval, null);
    }

    public StrategyRunnerV2(AtomicBoolean exited, Strategy strategy,
                            BlockingQueue<Notification> queue,
                            int interval, NotificationListener listener) {
        this.exited = exited;
        this.strategy = strategy;
        this.queue = queue;
        this.listener = listener;
        this.interval = interval;
    }

    @Override
    public void run() {

        while (!exited.get()) {
            try {
                Thread.sleep(interval);

                // 同步处理推送消息
                Notification notification;
                while ((notification = queue.peek()) != null) {
                    if (listener != null) {
                        listener.onNotification(notification);
                    }
                    strategy.notify(notification);
                    queue.remove();
                }

                strategy.run();
            } catch (InterruptedException e) {
                exited.set(true);
                logger.debug("strategy runner exited.");
            } catch (Throwable e) {
                logger.error("strategy running error", e);
            }
        }

        try {
            strategy.stop();
        } catch (Throwable e) {
            logger.error("failed to post run strategy", e);
        }
    }
}
