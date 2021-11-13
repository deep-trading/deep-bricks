package org.eurekaka.bricks.server.model;

import org.eurekaka.bricks.common.model.Notification;

public class TextNotification implements Notification {

    private final String name;
    private final String account;
    private final String text;

    public TextNotification(String name, String account, String text) {
        this.name = name;
        this.account = account;
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAccount() {
        return account;
    }

    @Override
    public String toString() {
        return "notification{" +
                "name='" + name + '\'' +
                ", account='" + account + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}
