package org.eurekaka.bricks.server.listener;

import org.eurekaka.bricks.api.NotificationListener;
import org.eurekaka.bricks.common.exception.NotificationException;
import org.eurekaka.bricks.common.model.TradeNotification;
import org.eurekaka.bricks.common.model.Notification;
import org.eurekaka.bricks.server.store.ExOrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryOrderListener implements NotificationListener {
    private final static Logger logger = LoggerFactory.getLogger(HistoryOrderListener.class);

    private final ExOrderStore orderStore;

    public HistoryOrderListener(ExOrderStore orderStore) {
        this.orderStore = orderStore;
    }

    @Override
    public void onNotification(Notification notification) throws NotificationException {
        try {
            if (notification instanceof TradeNotification) {
                orderStore.storeHistoryOrder((TradeNotification) notification);
            }
//            else {
//                logger.warn("received unknown notification, {}", notification);
//            }
        } catch (Exception e) {
            throw new NotificationException("history order listener error", e);
        }

    }
}
