package org.eurekaka.bricks.server.monitor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.eurekaka.bricks.common.model.Monitor;
import org.eurekaka.bricks.common.model.ReportEvent;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SlackMonitor implements Monitor {
    private final static Logger logger = LoggerFactory.getLogger(SlackMonitor.class);

    private ScheduledExecutorService executorService;

    private final boolean enabled;
    private Map<String, EventCounter> counterMap;
    // 告警最大内容长度
    private final int maxCount;
    private final int maxAlive;

    private HttpClient httpClient;

    private final String WEBHOOK;

    public SlackMonitor(Config config) {
        counterMap = new ConcurrentHashMap<>();

        WEBHOOK = config.getString("webhook");

        enabled = config.hasPath("enabled") && config.getBoolean("enabled");
        maxCount = config.hasPath("max_count") ? config.getInt("max_count") : 128;
        maxAlive = config.hasPath("max_alive") ? config.getInt("max_alive") : 60000;

        if (enabled) {
            Map<String, String> properties = new HashMap<>();
            for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
                properties.put(entry.getKey(), entry.getValue().unwrapped().toString());
            }
            httpClient = HttpUtils.initializeHttpClient(properties);

            executorService = Executors.newScheduledThreadPool(2);
            int period = config.hasPath("period") ? config.getInt("period") : 60;

            executorService.scheduleAtFixedRate(new EventReporter(this, counterMap, maxCount, maxAlive),
                    period, period, TimeUnit.SECONDS);
        }
    }

    @Override
    public void doReportEvent(String id, ReportEvent event) {
        if (enabled) {
            if (counterMap.containsKey(id)) {
                counterMap.get(id).inc(event.getContent());
            } else {
                EventCounter counter = new EventCounter(event);
                counterMap.put(id, counter);
                // 初始事件需要告警
                if (!doReport(event)) {
                    counter.count = maxCount;
                }
            }
        }
    }

    @Override
    public boolean doReport(ReportEvent event) {
        try {
            String text = "{ \"text\": \"" + Utils.getAppName() + ": " +
                    event.getEventType() + "\n" + event.getContent() + "\" }";
            HttpRequest request = HttpRequest.newBuilder(new URI(WEBHOOK))
                    .POST(HttpRequest.BodyPublishers.ofString(text))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return true;
            } else {
                logger.error("slack webhook request error: {}", response.body());
            }
        } catch (Exception e) {
            logger.error("failed to post event: {}", event, e);
        }

        return false;
    }

    @Override
    public void start() {
        logger.info("started slack webhook reporter");
    }

    @Override
    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

}
