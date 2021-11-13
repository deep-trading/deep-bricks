package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MultiStrategyManager implements StrategyManager {
    private final static Logger logger = LoggerFactory.getLogger(MultiStrategyManager.class);

    // 多策略模式，策略注册表
    private final Map<String, AtomicBoolean> strategyLocks;

    private final ExecutorService executorService;
    private final NotificationListener listener;
    private final BlockingQueue<Notification> blockingQueue;

    private final Map<String, BlockingQueue<Notification>> queueMap;
    private final StrategyFactory strategyFactory;

    public MultiStrategyManager(StrategyFactory strategyFactory,
                                BlockingQueue<Notification> blockingQueue,
                                NotificationListener listener) {
        this.blockingQueue = blockingQueue;
        this.listener = listener;

        this.strategyFactory = strategyFactory;

        this.strategyLocks = new ConcurrentHashMap<>();
        this.queueMap = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
    }

    @Override
    public void start() {
        executorService.execute(new NotificationDispatcher(
                new AtomicBoolean(false), queueMap, blockingQueue, listener));
        logger.info("multi strategy manager started.");
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
        boolean succ;
        try {
            succ = executorService.awaitTermination(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            succ = false;
        }
        logger.info("multi strategy manager exited, success: {}", succ);
    }

    @Override
    public void startStrategy(StrategyConfig strategyConfig) throws StrategyException {
        if (!strategyLocks.containsKey(strategyConfig.getName())) {
            Strategy strategy = strategyFactory.createStrategy(strategyConfig);
            strategy.start();
            int interval = strategyConfig.getInt("interval", 1000);
            AtomicBoolean exited = new AtomicBoolean(false);
            strategyLocks.put(strategyConfig.getName(), exited);
            BlockingQueue<Notification> queue = new LinkedBlockingQueue<>();
            queueMap.put(strategyConfig.getInfoName(), queue);
            executorService.execute(new StrategyRunnerV2(exited, strategy, queue, interval));
        }
    }

    @Override
    public void stopStrategy(StrategyConfig strategyConfig) throws StrategyException {
        if (strategyLocks.containsKey(strategyConfig.getName())) {
            AtomicBoolean exited = strategyLocks.get(strategyConfig.getName());
            exited.set(true);
            queueMap.remove(strategyConfig.getInfoName());
            strategyLocks.remove(strategyConfig.getName());
        }
    }


}
