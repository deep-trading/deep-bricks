package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncMultiStrategyManager implements StrategyManager {
    private final static Logger logger = LoggerFactory.getLogger(AsyncMultiStrategyManager.class);

    // 多策略模式，策略注册表
    private final Map<String, Strategy> strategyMap;
    private final Map<String, AtomicBoolean> strategyLocks;
    private final Map<String, AtomicBoolean> strategyNotifyLocks;
    private final Map<String, BlockingQueue<Notification>> queueMap;

//    private final StrategyFactoryManager factoryManager;
    private final StrategyFactory strategyFactory;
    private final ExecutorService executorService;
    private final NotificationListener listener;
    private final BlockingQueue<Notification> blockingQueue;

    public AsyncMultiStrategyManager(StrategyFactory strategyFactory,
                                     BlockingQueue<Notification> blockingQueue,
                                     NotificationListener listener) {
        this.blockingQueue = blockingQueue;
        this.listener = listener;

        this.strategyFactory = strategyFactory;

        this.strategyMap = new ConcurrentHashMap<>();
        this.strategyLocks = new ConcurrentHashMap<>();
        this.strategyNotifyLocks = new ConcurrentHashMap<>();
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
            AtomicBoolean exited1 = new AtomicBoolean(false);
            AtomicBoolean exited2 = new AtomicBoolean(false);
            strategyMap.put(strategyConfig.getName(), strategy);
            strategyLocks.put(strategyConfig.getName(), exited1);
            strategyNotifyLocks.put(strategyConfig.getName(), exited2);
            if (strategyConfig.getInfoName() != null) {
                if (!queueMap.containsKey(strategyConfig.getInfoName())) {
                    queueMap.put(strategyConfig.getInfoName(), new LinkedBlockingQueue<>());
                }
                executorService.execute(new StrategyRunnerV3(exited2,
                        strategy, queueMap.get(strategyConfig.getInfoName())));
            }
            executorService.execute(new StrategyRunnerV1(exited1, strategy, interval));
        }
    }

    @Override
    public void stopStrategy(StrategyConfig strategyConfig) throws StrategyException {
        if (strategyLocks.containsKey(strategyConfig.getName())) {
            strategyLocks.get(strategyConfig.getName()).set(true);
            strategyNotifyLocks.get(strategyConfig.getName()).set(true);
//            if (strategyConfig.getInfoName() != null) {
//                queueMap.remove(strategyConfig.getInfoName());
//            }
            strategyMap.remove(strategyConfig.getName());
            strategyLocks.remove(strategyConfig.getName());
            strategyNotifyLocks.remove(strategyConfig.getName());
        }
    }

    @Override
    public void notifyStrategy(Notification notification) throws StrategyException {
        if (!strategyMap.containsKey(notification.getName())) {
            throw new StrategyException("unknown strategy, notify failed.");
        }
        strategyMap.get(notification.getName()).notify(notification);
    }
}
