package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.NotificationException;
import org.eurekaka.bricks.common.model.Notification;

/**
 * 不定时执行策略，根据接收到的消息触发相应的动作
 * 与strategy配合使用
 */
public interface NotificationListener {
    /**
     * 通知处理器，接收处理从exchange的账户下接收的通知
     * @param notification 不同的消息体，比如成交信息，订单消息，市场价格等
     */
    void onNotification(Notification notification) throws NotificationException;

}
