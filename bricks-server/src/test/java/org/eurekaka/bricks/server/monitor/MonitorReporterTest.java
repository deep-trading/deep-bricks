package org.eurekaka.bricks.server.monitor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.common.model.ReportEvent;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.eurekaka.bricks.common.util.Utils;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MonitorReporterTest {


    @Test
    public void testMonitorReporter() throws Exception {
        Config wechat = ConfigFactory.load("common.conf").getConfig("server.monitor");
        HashMap<String, Object> configMap = new HashMap<>();
        List<Integer> tags = new ArrayList<>();
        tags.add(100);
        configMap.put("secret", "123456");
        configMap.put("enabled", true);
        configMap.put("max_count", 5);
        configMap.put("period", 1);
//        configMap.put("tags", tags);
//        configMap.put("wechat.key", wechat.getString("wechat.key"));
//        configMap.put("wechat.secret", wechat.getString("wechat.secret"));
//        configMap.put("clz", "org.eurekaka.bricks.server.monitor.WeChatMonitor");
        configMap.put("webhook", "https://hooks.slack.com/services/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");
        configMap.put("clz", "org.eurekaka.bricks.server.monitor.SlackMonitor");

        configMap.put("http_proxy_host", "localhost");
        configMap.put("http_proxy_port", 8123);

        configMap.put("timezone", "+08");
        configMap.put("app_name", "test");
        configMap.put("auth_password", "xxx");
        configMap.put("auth_salt", "xxx");

        Config monitorConfig = ConfigFactory.parseMap(configMap);
        Utils.checkConfig(monitorConfig);

        MonitorReporter.start(monitorConfig);

        int count = 0;
        while (count < 10) {
            ReportEvent event = new ReportEvent(ReportEvent.EventType.HEDGING_MAKE_ORDER_FAILED,
                    ReportEvent.EventLevel.SERIOUS, "我在测试" + count);
            MonitorReporter.report("k1", event);
            Thread.sleep(1000);
            count++;
        }

        MonitorReporter.stop();
    }
}
