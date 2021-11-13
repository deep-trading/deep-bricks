package org.eurekaka.bricks.server.monitor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.Monitor;
import org.eurekaka.bricks.common.model.ReportEvent;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;
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

public class WeChatMonitor implements Monitor {
    private final static Logger logger = LoggerFactory.getLogger(WeChatMonitor.class);

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

    public WeChatMonitor(Config config) {
        counterMap = new ConcurrentHashMap<>();

        this.token = "";
        tags = new HashSet<>();
        if (config.hasPath("tags")) {
            tags.addAll(config.getIntList("tags"));
        }
        enabled = config.hasPath("enabled") && config.getBoolean("enabled");
        maxLength = config.hasPath("max_length") ? config.getInt("max_length") : 128;
        maxCount = config.hasPath("max_count") ? config.getInt("max_count") : 128;
        maxAlive = config.hasPath("max_alive") ? config.getInt("max_alive") : 60000;

        users = new HashSet<>();

        if (config.hasPath("wechat")) {
            String key = config.getString("wechat.key");
            String secret = config.getString("wechat.secret");
            TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?" +
                    "grant_type=client_credential&appid=" + key +"&secret=" + secret;
            useWeChatToken = true;
        } else {
            this.serverSecret = config.getString("secret");
            TOKEN_URL = "";
            useWeChatToken = false;
        }

        TEMPLATE_ID = config.hasPath("template_id") ?
                config.getString("template_id") :
                "u1mcPGYuydrtLZ6lQmRRJ_LqJgpyoScRx6gr1BRjv8w";

        TOKEN_CHECK = "https://api.weixin.qq.com/cgi-bin/getcallbackip?access_token=";

        TEMPLATE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=";

        OPENID_URL = "https://api.weixin.qq.com/cgi-bin/user/tag/get?access_token=";

        SERVER_URL = "https://www.eurekaka.org/openapi/v1/monitor/auth";

        Map<String, String> properties = new HashMap<>();
        for (Map.Entry<String, ConfigValue> entry : config.entrySet()) {
            properties.put(entry.getKey(), entry.getValue().unwrapped().toString());
        }
        httpClient = HttpUtils.initializeHttpClient(properties);

        executorService = Executors.newScheduledThreadPool(2);
        int period = config.hasPath("period") ? config.getInt("period") : 60;
        boolean auto_update = config.hasPath("auto_update") && config.getBoolean("auto_update");

        if (enabled) {
            executorService.scheduleAtFixedRate(new EventReporter(this, counterMap, maxCount, maxAlive),
                    period, period, TimeUnit.SECONDS);
        }

        if (auto_update) {
            executorService.scheduleAtFixedRate(new WeChatTokenUpdater(), 0, period, TimeUnit.SECONDS);
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
    public void start() {
        if (enabled) {
            try {
                fetchToken();
                fetchUsers();
            } catch (Exception e) {
                throw new InitializeException("failed to start wechat monitor", e);
            }
        }
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }

    private void fetchUsers() throws Exception {
        users.clear();
        for (Integer tagId : tags) {
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

    @Override
    public boolean doReport(ReportEvent event) {
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
