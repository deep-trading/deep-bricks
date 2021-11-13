package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.model.TextNotification;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class StrategyManagerTest {

    @Test
    public void testSingleStrategyManager() throws Exception {
        Strategy strategy = new TestStrategy("n1");
        NotificationListener listener = new TestNotificationListener();

        LinkedBlockingQueue<Notification> queue = new LinkedBlockingQueue<>();

        SingleStrategyManager strategyManager = new SingleStrategyManager(strategy, queue, listener);
        strategyManager.start();

        Thread.sleep(1500);
        queue.add(new TextNotification("n1", "e1", "text1"));
        queue.add(new TextNotification("n1", "e1", "text2"));

        Thread.sleep(2800);
        queue.add(new TextNotification("n1", "e1", "text3"));
        queue.add(new TextNotification("n1", "e1", "text4"));

        Thread.sleep(1200);

        strategyManager.stop();
    }

    @Test
    public void testAsyncSingleStrategyManager() throws Exception {
        Strategy strategy = new TestStrategy("n1");
        NotificationListener listener = new TestNotificationListener();

        LinkedBlockingQueue<Notification> queue = new LinkedBlockingQueue<>();

        AsyncSingleStrategyManager strategyManager = new AsyncSingleStrategyManager(strategy, queue, listener);
        strategyManager.start();

        Thread.sleep(1500);
        queue.add(new TextNotification("n1", "e1", "text1"));
        queue.add(new TextNotification("n1", "e1", "text2"));

        Thread.sleep(2800);
        queue.add(new TextNotification("n1", "e1", "text3"));
        queue.add(new TextNotification("n1", "e1", "text4"));

        Thread.sleep(1200);

        strategyManager.stop();
    }

    @Test
    public void testMultiStrategyManager() throws Exception {
        NotificationListener listener = new TestNotificationListener();

        LinkedBlockingQueue<Notification> queue = new LinkedBlockingQueue<>();

        StrategyConfig strategyConfig = new StrategyConfig(1,
                "sn1", "c1", "n1", true, Map.of());

        StrategyFactory strategyFactory = new TestStrategyFactory();
        MultiStrategyManager strategyManager = new MultiStrategyManager(strategyFactory, queue, listener);
        strategyManager.start();

        strategyManager.startStrategy(strategyConfig);

        Thread.sleep(1500);
        queue.add(new TextNotification("n1", "e1", "text1"));
        queue.add(new TextNotification("n1", "e1", "text2"));

        Thread.sleep(2800);
        queue.add(new TextNotification("n1", "e1", "text3"));
        queue.add(new TextNotification("n1", "e1", "text4"));

        Thread.sleep(1200);

        strategyManager.stopStrategy(strategyConfig);

        strategyManager.stop();
    }


    @Test
    public void testAsyncMultiStrategyManager() throws Exception {
        NotificationListener listener = new TestNotificationListener();

        LinkedBlockingQueue<Notification> queue = new LinkedBlockingQueue<>();

        StrategyConfig strategyConfig = new StrategyConfig(1,
                "sn1", "c1", "n1", true, Map.of());

        StrategyFactory strategyFactory = new TestStrategyFactory();
        AsyncMultiStrategyManager strategyManager = new AsyncMultiStrategyManager(strategyFactory, queue, listener);
        strategyManager.start();

        strategyManager.startStrategy(strategyConfig);

        Thread.sleep(1500);
        queue.add(new TextNotification("n1", "e1", "text1"));
        queue.add(new TextNotification("n1", "e1", "text2"));

        Thread.sleep(2800);
        queue.add(new TextNotification("n1", "e1", "text3"));
        queue.add(new TextNotification("n1", "e1", "text4"));

        Thread.sleep(1200);

        strategyManager.stopStrategy(strategyConfig);

        strategyManager.stop();
    }
}
