package org.eurekaka.bricks.common.model;

public interface Copyable<T extends Copyable<T>> {

    /**
     * 动态参数更新，用于内部状态更新时，更新收到的最新状态
     * @param other 收到的最新状态
     */
    void copy(T other);
}
