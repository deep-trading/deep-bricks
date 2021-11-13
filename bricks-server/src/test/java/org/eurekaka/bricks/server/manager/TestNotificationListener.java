package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.common.exception.NotificationException;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.server.model.TextNotification;

public class TestNotificationListener  implements NotificationListener {

    public TestNotificationListener() {
    }

    @Override
    public void onNotification(Notification notification) throws NotificationException {
        if (notification instanceof TextNotification) {
            System.out.println("listener: " + ((TextNotification) notification).getText());
        } else {
            System.out.println(notification);
        }
    }
}
