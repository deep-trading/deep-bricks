package org.eurekaka.bricks.server.stat;

import org.eurekaka.bricks.common.exception.StatException;

public interface Stat {
    long MINUTE = 60 * 1000;
    long HOUR = 60 * 60 * 1000;
    long EIGHT_HOUR = 8 * 60 * 60 * 1000;
    long DAY = 24 * 60 * 60 * 1000;

    /**
     * 执行stat任务
     * @throws StatException 执行失败
     */
    void execute() throws StatException;

    /**
     * 获取下一个执行任务的时间点
     * @return unix timestamp
     */
    long getNextTime();

    /**
     * 更新下一个执行任务的时间点
     */
    void updateNextTime();
}
