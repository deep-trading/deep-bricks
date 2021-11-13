package org.eurekaka.bricks.server.monitor;

import org.eurekaka.bricks.common.model.Monitor;

import java.util.Map;

public class EventReporter implements Runnable {
    private final Monitor monitor;
    private final Map<String, EventCounter> counterMap;
    private final int maxCount;
    private final int maxAlive;

    public EventReporter(Monitor monitor,
                         Map<String, EventCounter> counterMap,
                         int maxCount, int maxAlive) {
        this.monitor = monitor;
        this.counterMap = counterMap;
        this.maxCount = maxCount;
        this.maxAlive = maxAlive;
    }


    @Override
    public void run() {
        long currentTime = System.currentTimeMillis();
        counterMap.entrySet().removeIf(entry -> currentTime - entry.getValue().lastTime > maxAlive);
        for (EventCounter counter : counterMap.values()) {
            if (counter.count >= maxCount ||
                    counter.lastTime - counter.firstTime >= maxAlive) {
                if (monitor.doReport(counter.event)) {
                    counter.reset();
                }
            }
        }
    }
}
