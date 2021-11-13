package org.eurekaka.bricks.common.model;

public interface Monitor {

    void doReportEvent(String id, ReportEvent event);

    boolean doReport(ReportEvent event);

    void start();

    void stop();
}
