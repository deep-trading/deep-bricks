package org.eurekaka.bricks.server.stat;

import org.eurekaka.bricks.common.exception.StatException;
import org.junit.Test;

public class StatManagerTest {

    @Test
    public void testStatManager() throws Exception {
        StatManager manager = new StatManager();
        manager.registerStat(new Stat() {
            private long nextTime = System.currentTimeMillis() / 1000 * 1000;

            @Override
            public void execute() throws StatException {
                System.out.println("current time: " + System.currentTimeMillis() + ", next time: " + nextTime);
            }

            @Override
            public long getNextTime() {
                return nextTime;
            }

            @Override
            public void updateNextTime() {
                nextTime += 1000;
            }
        });
        manager.start();

        int count = 0;
        while (count++ < 5) {
            Thread.sleep(1000);
        }

        manager.stop();
    }
}
