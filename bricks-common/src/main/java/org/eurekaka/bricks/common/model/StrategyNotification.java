package org.eurekaka.bricks.common.model;

import java.util.Map;
import java.util.Objects;

public class StrategyNotification<T> implements Notification {
    // strategy name
    private final String name;
    // 存储通知内容
    private final T data;

    public StrategyNotification(String name, T data) {
        this.name = name;
        this.data = data;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getAccount() {
        return null;
    }

    public T getData() {
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StrategyNotification)) return false;
        StrategyNotification<?> that = (StrategyNotification<?>) o;
        return name.equals(that.name) && data.equals(that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, data);
    }

    @Override
    public String toString() {
        return "StrategyNotification{" +
                "name='" + name + '\'' +
                ", data=" + data +
                '}';
    }
}
