package org.eurekaka.bricks.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.eurekaka.bricks.common.model.ReportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeChatReporter {
    private static final Logger logger = LoggerFactory.getLogger(WeChatReporter.class);
    private static WeChatReporter instance;

    /**
     * 发送消息告警
     * 根据id标记唯一的消息，同一个消息在一定时间内避免重复告警
     * @param id 消息id
     * @param event 告警事件
     */
    public static void report(String id, ReportEvent event) {
        if (instance != null) {
            instance.doReportEvent(id, event);
        }
    }

    public static void start(Config config) {
        if (instance == null) {
            instance = new WeChatReporter(config);
        }
        try {
            if (instance.enabled) {
                instance.fetchToken();
                instance.fetchUsers();
            }
        } catch (Exception e) {
            logger.error("failed to initial the wechat reporter", e);
        }
    }

    public static void stop() {
        instance.executorService.shutdownNow();
    }

    public static String getToken() {
        return instance.token;
    }

    public static void setToken(String token) {
        instance.token = token;
    }

    public static Set<String> updateUsers() {
        instance.fetchUsers();
        return instance.users;
    }

    private ScheduledExecutorService executorService;
    private Map<String, EventCounter> counterMap;
    private Set<Integer> tags;

    private String token;
    //    private long tokenExpireTime;
    private Set<String> users;
    private boolean enabled;
    private boolean useWeChatToken;
    private String serverSecret;

    private final String TOKEN_URL;
    private final String TOKEN_CHECK;
    private final String TEMPLATE_URL;
    private final String SERVER_URL;
    private final String OPENID_URL;
    private final HttpClient httpClient;
    private final String TEMPLATE_ID;

    private final int maxLength;
    private final int maxCount;
    private final int maxAlive;

    private WeChatReporter(Config monitor) {
        counterMap = new ConcurrentHashMap<>();

        this.token = "";
        tags = new HashSet<>();
        if (monitor.hasPath("tags")) {
            tags.addAll(monitor.getIntList("tags"));
        }
        enabled = monitor.hasPath("enabled") && monitor.getBoolean("enabled");
        maxLength = monitor.hasPath("max_length") ? monitor.getInt("max_length") : 128;
        maxCount = monitor.hasPath("max_count") ? monitor.getInt("max_count") : 128;
        maxAlive = monitor.hasPath("max_alive") ? monitor.getInt("max_alive") : 60000;

        users = new HashSet<>();

        if (monitor.hasPath("wechat")) {
            String key = monitor.getString("wechat.key");
            String secret = monitor.getString("wechat.secret");
            TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?" +
                    "grant_type=client_credential&appid=" + key +"&secret=" + secret;
            useWeChatToken = true;
        } else {
            this.serverSecret = monitor.getString("secret");
            TOKEN_URL = "";
            useWeChatToken = false;
        }

        TEMPLATE_ID = monitor.hasPath("template_id") ?
                monitor.getString("template_id") :
                "u1mcPGYuydrtLZ6lQmRRJ_LqJgpyoScRx6gr1BRjv8w";

        TOKEN_CHECK = "https://api.weixin.qq.com/cgi-bin/getcallbackip?access_token=";

        TEMPLATE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";

        OPENID_URL = "https://api.weixin.qq.com/cgi-bin/user/tag/get?access_token=";

        SERVER_URL = "https://www.eurekaka.org/openapi/v1/monitor/auth";

        Map<String, String> properties = new HashMap<>();
        for (Map.Entry<String, ConfigValue> entry : monitor.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().render());
        }
        httpClient = HttpUtils.initializeHttpClient(properties);

        executorService = Executors.newScheduledThreadPool(2);
        int period = monitor.hasPath("period") ? monitor.getInt("period") : 60;
        boolean auto_update = monitor.hasPath("auto_update") && monitor.getBoolean("auto_update");

        if (enabled) {
            executorService.scheduleAtFixedRate(new EventReporter(), period, period, TimeUnit.SECONDS);
        }

        if (auto_update) {
            executorService.scheduleAtFixedRate(new WeChatTokenUpdater(), 0, period, TimeUnit.SECONDS);
        }
    }

    private void fetchUsers() {
        users.clear();
        for (Integer tagId : tags) {
            try {
                HttpRequest request = HttpRequest.newBuilder(new URI(OPENID_URL + token))
                        .POST(HttpRequest.BodyPublishers.ofString("{   \"tagid\" : " + tagId + " }"))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                WeChatMsg resp = Utils.mapper.readValue(response.body(), WeChatMsg.class);
                if (resp.data != null && resp.data.openid != null) {
                    users.addAll(resp.data.openid);
                } else {
                    throw new IOException("error resp: " + resp);
                }
            } catch (Exception e) {
                logger.warn("failed to update users: {}", users, e);
            }
        }
        logger.info("current wechat monitor user: {}", users);
    }

    private void fetchToken() throws Exception {
        if (useWeChatToken) {
            HttpRequest request = HttpRequest.newBuilder(new URI(TOKEN_URL)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            WeChatMsg resp = Utils.mapper.readValue(response.body(), WeChatMsg.class);
            if (resp.errcode == 0) {
                token = resp.access_token;
//                tokenExpireTime = System.currentTimeMillis() + resp.expires_in * 1000 - 600 * 1000;
            } else {
                throw new IOException("error resp: " + resp);
            }
        } else {
            HttpRequest request = HttpRequest.newBuilder(new URI(SERVER_URL))
                    .GET().header("Authorization", serverSecret).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, String> resp = Utils.mapper.readValue(response.body(), new TypeReference<>() {});
            if (resp != null && resp.containsKey("result")) {
                token = resp.get("result");
            } else {
                logger.error("failed to get token from server, {}", response.body());
            }
        }
    }

    private void doReportEvent(String id, ReportEvent event) {
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

    private boolean doReport(ReportEvent event) {
        if (!retryDoReport(event)) {
            try {
                fetchToken();
            } catch (Exception e) {
                logger.error("failed to fetch token", e);
                return false;
            }
            return retryDoReport(event);
        }
        return true;
    }

    private boolean retryDoReport(ReportEvent event) {
        try {
            List<WeChatTemplate> messages = buildData(event);
            for (WeChatTemplate message : messages) {
                HttpRequest request = HttpRequest.newBuilder(new URI(TEMPLATE_URL + token))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                Utils.mapper.writeValueAsString(message)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                WeChatMsg resp = Utils.mapper.readValue(response.body(), WeChatMsg.class);
                if (resp.errcode != 0) {
                    throw new IOException("errcode not 0, resp: " + resp);
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("failed to send message: {}", event, e);
        }
        return false;
    }

    private List<WeChatTemplate> buildData(ReportEvent event) {
        List<WeChatTemplate> templates = new ArrayList<>();
        for (String user : users) {
            Map<String, ValueWrapper> data = new HashMap<>();
            data.put("first", new ValueWrapper(event.getEventType().getDescription(),
                    event.getLevel().getColor()));
            int len = Math.min(maxLength, event.getContent().length());
            data.put("content", new ValueWrapper(event.getContent().substring(0, len)));
            data.put("occurtime", new ValueWrapper(Utils.formatTime(System.currentTimeMillis())));
            data.put("remark", new ValueWrapper(Utils.getAppName()));
            templates.add(new WeChatTemplate(user, data, TEMPLATE_ID));
        }
        return templates;
    }

    private boolean checkToken() {
        WeChatMsg resp = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(TOKEN_CHECK + token)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            resp = Utils.mapper.readValue(response.body(), WeChatMsg.class);
            if (resp.errcode == 0) {
                return true;
            }
        } catch (Exception e) {
            logger.error("token is not available, resp: {}", resp, e);
        }
        return false;
    }

    private class EventReporter implements Runnable {

        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            counterMap.entrySet().removeIf(entry -> currentTime - entry.getValue().lastTime > maxAlive);
            for (EventCounter counter : counterMap.values()) {
                if (counter.count >= maxCount ||
                        counter.lastTime - counter.firstTime >= maxAlive) {
                    if (doReport(counter.event)) {
                        counter.reset();
                    }
                }
            }
        }

    }

    // 每分钟自动检查token，若失效则自动刷新
    class WeChatTokenUpdater implements Runnable {

        @Override
        public void run() {
            try {
                if (!checkToken()) {
                    logger.warn("token is not available, refetch again: {}", token);
                    fetchToken();
                }
            } catch (Exception e) {
                logger.error("failed to update token", e);
            }
        }
    }


    private static class WeChatMsg {
        private int errcode;
        private String errmsg;

        private String access_token;
        private int expires_in;

        private WeChatData data;

        public WeChatMsg() {
        }

        public int getErrcode() {
            return errcode;
        }

        public void setErrcode(int errcode) {
            this.errcode = errcode;
        }

        public String getErrmsg() {
            return errmsg;
        }

        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }

        public String getAccess_token() {
            return access_token;
        }

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }

        public int getExpires_in() {
            return expires_in;
        }

        public void setExpires_in(int expires_in) {
            this.expires_in = expires_in;
        }

        public WeChatData getData() {
            return data;
        }

        public void setData(WeChatData data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "WeChatMsg{" +
                    "errcode=" + errcode +
                    ", errmsg='" + errmsg + '\'' +
                    ", access_token='" + access_token + '\'' +
                    ", expires_in=" + expires_in +
                    ", data=" + data +
                    '}';
        }
    }

    private static class WeChatData {
        private List<String> openid;

        public WeChatData() {
        }

        public List<String> getOpenid() {
            return openid;
        }

        public void setOpenid(List<String> openid) {
            this.openid = openid;
        }

        @Override
        public String toString() {
            return "WeChatData{" +
                    "openid=" + openid +
                    '}';
        }
    }


    private static class EventCounter {
        // 第一次告警时间
        private long firstTime;
        // 最近一次告警时间
        private long lastTime;
        // 告警次数
        private int count;

        private ReportEvent event;

        public EventCounter(ReportEvent event) {
            this.firstTime = this.lastTime = System.currentTimeMillis();
            this.count = 1;
            this.event = event;
        }

        public void inc() {
            this.lastTime = System.currentTimeMillis();
            count++;
        }

        public void inc(String message) {
            inc();
            this.event.setContent(message);
        }

        public void reset() {
            this.firstTime = this.lastTime = System.currentTimeMillis();
            this.count = 1;
        }

    }




    static class WeChatTemplate {
        public String touser;
        public String template_id;
        public Map<String, ValueWrapper> data;

        public WeChatTemplate() {
        }

        public WeChatTemplate(String touser, Map<String, ValueWrapper> data, String template_id) {
            this.touser = touser;
            this.data = data;
            this.template_id = template_id;
        }

    }

    static class ValueWrapper {
        public String value;
        public String color;

        public ValueWrapper() {
        }

        public ValueWrapper(String value) {
            this.value = value;
            this.color = "#000000";
        }

        public ValueWrapper(String value, String color) {
            this.value = value;
            this.color = color;
        }
    }



}
