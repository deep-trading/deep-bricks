package org.eurekaka.bricks.server.stat;

import org.eurekaka.bricks.common.exception.StatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注册并且定时执行任务
 */
public class StatManager {
    private final static Logger logger = LoggerFactory.getLogger(StatManager.class);

    private final List<StatRunner> stats;
    private final ExecutorService executorService;

    public StatManager() {
        this.stats = new ArrayList<>();
        this.executorService = Executors.newCachedThreadPool();
    }

    public void registerStat(Stat stat) {
        this.stats.add(new StatRunner(stat));
    }

    public void start() {
        for (StatRunner stat : stats) {
            this.executorService.execute(stat);
        }
    }

    public void stop() {
        for (StatRunner stat : stats) {
            stat.stop();
        }
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("stat manager interrupted");
        }
    }

    static class StatRunner implements Runnable {
        private final Stat stat;
        private final AtomicBoolean exited;

        public StatRunner(Stat stat) {
            this.stat = stat;
            this.exited = new AtomicBoolean(false);
        }

        @Override
        public void run() {
            while (!exited.get()) {
                try {
                    Thread.sleep(1000);

                    if (System.currentTimeMillis() > stat.getNextTime()) {
                        try {
                            stat.execute();
                        } catch (Exception e) {
                            logger.error("failed to run stat: ", e);
                        }
                        stat.updateNextTime();
                    }
                } catch (InterruptedException e) {
                    exited.set(true);
                }
            }
        }

        public void stop() {
            this.exited.set(true);
        }
    }
}
