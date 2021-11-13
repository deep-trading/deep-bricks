package org.eurekaka.bricks.common.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.common.model.ReportEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class WeChatReporterTest {

    public static void main(String[] args) throws Exception {
        HashMap<String, Object> configMap = new HashMap<>();
        List<Integer> tags = new ArrayList<>();
        tags.add(100);
        configMap.put("secret", "123456");
        configMap.put("enabled", true);
        configMap.put("max_count", 5);
        configMap.put("period", 1);
        configMap.put("tags", tags);
        configMap.put("wechat.key", "wxfe992bc556db1719");
        configMap.put("wechat.secret", "4c5580722b5d26a8409079a01c20c36b");

        configMap.put("timezone", "+08");
        configMap.put("app_name", "test");
        configMap.put("auth_password", "xxx");
        configMap.put("auth_salt", "xxx");

        Config monitorConfig = ConfigFactory.parseMap(configMap);
        Utils.checkConfig(monitorConfig);

        WeChatReporter.start(monitorConfig);

        int count = 0;
        while (count < 10) {
            ReportEvent event = new ReportEvent(ReportEvent.EventType.HEDGING_MAKE_ORDER_FAILED,
                    ReportEvent.EventLevel.SERIOUS, "我在测试" + count);
            WeChatReporter.report("k1", event);
            Thread.sleep(1000);
            count++;
        }
    }

}
