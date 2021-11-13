package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.model.ReportEvent;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.eurekaka.bricks.common.model.ReportEvent.EventLevel.SERIOUS;
import static org.eurekaka.bricks.common.model.ReportEvent.EventType.STRATEGY_FAILED;

/**
 * 策略定时执行器
 */
public class StrategyRunnerV1 implements Runnable {
    private final static Logger logger = LoggerFactory.getLogger(StrategyRunnerV1.class);

    private final AtomicBoolean exited;
    private final Strategy strategy;
    private final int interval;
    private final List<Long> exceptionQueue;
    private long nextTime;

    public StrategyRunnerV1(AtomicBoolean exited, Strategy strategy) {
        this(exited, strategy, 1000);
    }

    public StrategyRunnerV1(AtomicBoolean exited, Strategy strategy, int interval) {
        this.exited = exited;
        this.strategy = strategy;
        this.interval = interval;
        exceptionQueue = new ArrayList<>(20);
        nextTime = strategy.nextTime();
    }

    @Override
    public void run() {

        while (!exited.get()) {
            try {
                Thread.sleep(interval);

                if (nextTime > 0) {
                    if (System.currentTimeMillis() > nextTime) {
                        strategy.run();
                        nextTime = strategy.nextTime();
                    }
                } else {
                    strategy.run();
                }
            } catch (InterruptedException e) {
                exited.set(true);
//                logger.debug("strategy runner exited.");
            } catch (Throwable e) {
                // 异常检测，failed count > 3 in 10 circles
                logger.error("strategy running error", e);
                exceptionQueue.add(System.currentTimeMillis());
                if (exceptionQueue.size() > 7) {
                    MonitorReporter.report(STRATEGY_FAILED.name(),
                            new ReportEvent(STRATEGY_FAILED, SERIOUS, e.getMessage()));
                    exceptionQueue.clear();
                }
            }
        }

        try {
            strategy.stop();
        } catch (Throwable e) {
            logger.error("failed to post run strategy", e);
        }

        this.exited.set(true);
    }
}
