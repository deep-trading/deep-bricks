package org.eurekaka.bricks.common.util;

import com.typesafe.config.Config;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.Monitor;
import org.eurekaka.bricks.common.model.ReportEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class MonitorReporter {
    private static Monitor instance;

    public static void report(String id, ReportEvent event) {
        if (instance != null) {
            instance.doReportEvent(id, event);
        }
    }

    public static void start(Config config) {
        if (instance == null) {
            instance = createMonitor(config);
        }
        if (instance != null) {
            instance.start();
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.stop();
        }
    }

    public static Monitor getInstance() {
        return instance;
    }

    private static Monitor createMonitor(Config config) {
        String clzName = config.hasPath("clz") ? config.getString("clz") : null;
        if (clzName == null) {
            return null;
        }
        try {
            Class<?> clz = Class.forName(clzName);
            Constructor<?> constructor = clz.getConstructor(Config.class);
            return (Monitor) constructor.newInstance(config);
        } catch (ClassNotFoundException | NoSuchMethodException |
                InstantiationException | IllegalAccessException |
                InvocationTargetException e) {
            throw new InitializeException("failed to create monitor", e);
        }
    }

}
