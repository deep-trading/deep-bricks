package org.eurekaka.bricks.server.monitor;

import org.eurekaka.bricks.common.model.ReportEvent;

public class EventCounter {
    // 第一次告警时间
    long firstTime;
    // 最近一次告警时间
    long lastTime;
    // 告警次数
    int count;

    ReportEvent event;

    public EventCounter(ReportEvent event) {
        this.firstTime = this.lastTime = System.currentTimeMillis();
        this.count = 1;
        this.event = event;
    }

    public void inc() {
        this.lastTime = System.currentTimeMillis();
        count++;
    }

    public void inc(String message) {
        inc();
        this.event.setContent(message);
    }

    public void reset() {
        this.firstTime = this.lastTime = System.currentTimeMillis();
        this.count = 1;
    }
}
