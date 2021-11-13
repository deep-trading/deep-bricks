package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncSingleStrategyManager {
    private final static Logger logger = LoggerFactory.getLogger(AsyncSingleStrategyManager.class);

    // 单策略模式
    private final Strategy strategy;

    private final ExecutorService executorService;
    private final NotificationListener listener;
    private final BlockingQueue<Notification> queue;

    public AsyncSingleStrategyManager(Strategy strategy,
                                      BlockingQueue<Notification> queue,
                                      NotificationListener listener) {
        this.strategy = strategy;
        this.listener = listener;
        this.queue = queue;

        this.executorService = Executors.newFixedThreadPool(2);
    }

    public void start() {
        executorService.execute(new StrategyRunnerV1(new AtomicBoolean(false), strategy));
        executorService.execute(new StrategyRunnerV3(
                new AtomicBoolean(false), strategy, queue, listener));

        logger.info("async strategy manager started.");
    }

    public void stop() {
        executorService.shutdownNow();
        boolean succ;
        try {
            succ = executorService.awaitTermination(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            succ = false;
        }
        logger.info("strategy manager exited, success: {}", succ);
    }

}
