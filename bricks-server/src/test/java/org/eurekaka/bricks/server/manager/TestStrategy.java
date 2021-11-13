package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.Notification;

public class TestStrategy implements Strategy {
    private final long startTime;
    private final String name;

    public TestStrategy(String name) {
        this.name = name;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public void run() throws StrategyException {
        System.out.println(name + " running at " + (System.currentTimeMillis() - startTime));
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            System.out.println("test strategy interrupted");
//        }
    }

    @Override
    public void notify(Notification notification) throws StrategyException {
        System.out.println(name + " notify at: " + (System.currentTimeMillis() - startTime) + ", " + notification);
    }
}
