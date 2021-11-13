package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AccountConfig implements Copyable<AccountConfig> {
    // 数据库id
    private int id;

    // 系统内定义的账户名称
    private String name;
    // 一个系统内可能存在不同的账户类型，由系统开发时初始化自动选择
    // 账户类型，根据账户类型选择对应的初始化操作
    // 1, 2, 3...
    private int type;

    // 对应的agent类
    private String clz;
    @JsonProperty("listener_clz")
    private String listenerClz;
    @JsonProperty("api_clz")
    private String apiClz;
    private String websocket;
    // http 接口地址
    private String url;

    // 对应平台的账户名称id
    private String uid;

    // 存储加密后的私钥公钥，iv为initialize vector
    @JsonProperty("auth_key")
    private String authKey;
    @JsonProperty("auth_secret")
    private String authSecret;

    private boolean enabled;

    // 其他配置信息
    private final Map<String, String> properties;

    // 可以动态改变的参数部分
    // 优先级
    private int priority;
    // taker 与 maker的费率
    @JsonProperty("taker_rate")
    private double takerRate;
    @JsonProperty("maker_rate")
    private double makerRate;

    public AccountConfig() {
        this.properties = new HashMap<>();
    }

    public AccountConfig(int id, String name, int type, String clz,
                         String listenerClz, String apiClz,
                         String websocket, String url,
                         String uid, String authKey,
                         String authSecret, boolean enabled) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.clz = clz;
        this.listenerClz = listenerClz;
        this.apiClz = apiClz;
        this.websocket = websocket;
        this.url = url;
        this.uid = uid;
        this.authKey = authKey;
        this.authSecret = authSecret;
        this.enabled = enabled;

        this.priority = 1;

        this.properties = new HashMap<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public String getClz() {
        return clz;
    }

    public String getListenerClz() {
        return listenerClz;
    }

    public String getApiClz() {
        return apiClz;
    }

    public String getWebsocket() {
        return websocket;
    }

    public String getUrl() {
        return url;
    }

    public String getUid() {
        return uid;
    }

    public int getPriority() {
        return priority;
    }

    public String getAuthKey() {
        return authKey;
    }

    public String getAuthSecret() {
        return authSecret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getTakerRate() {
        return takerRate;
    }

    public double getMakerRate() {
        return makerRate;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setTakerRate(double takerRate) {
        this.takerRate = takerRate;
    }

    public void setMakerRate(double makerRate) {
        this.makerRate = makerRate;
    }

    public void setProperty(String key, String value) {
        this.properties.put(key, value);
    }

    public void setProperties(Map<String, String> props) {
        this.properties.putAll(props);
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setClz(String clz) {
        this.clz = clz;
    }

    public void setListenerClz(String listenerClz) {
        this.listenerClz = listenerClz;
    }

    public void setApiClz(String apiClz) {
        this.apiClz = apiClz;
    }

    public void setWebsocket(String websocket) {
        this.websocket = websocket;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public void setAuthSecret(String authSecret) {
        this.authSecret = authSecret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountConfig)) return false;
        AccountConfig that = (AccountConfig) o;
        return id == that.id &&
                type == that.type &&
                enabled == that.enabled &&
                priority == that.priority &&
                Double.compare(that.takerRate, takerRate) == 0 &&
                Double.compare(that.makerRate, makerRate) == 0 &&
                name.equals(that.name) &&
                clz.equals(that.clz) &&
                Objects.equals(listenerClz, that.listenerClz) &&
                apiClz.equals(that.apiClz) &&
                Objects.equals(websocket, that.websocket) &&
                Objects.equals(url, that.url) &&
                Objects.equals(uid, that.uid) &&
                authKey.equals(that.authKey) &&
                authSecret.equals(that.authSecret) &&
                properties.equals(that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, clz, listenerClz, apiClz, websocket, url, uid,
                authKey, authSecret, enabled, properties, priority, takerRate, makerRate);
    }

    @Override
    public String toString() {
        return "AccountConfig{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", clz='" + clz + '\'' +
                ", listenerClz='" + listenerClz + '\'' +
                ", apiClz='" + apiClz + '\'' +
                ", websocket='" + websocket + '\'' +
                ", url='" + url + '\'' +
                ", uid='" + uid + '\'' +
                ", enabled=" + enabled +
                ", properties=" + properties +
                ", priority=" + priority +
                ", takerRate=" + takerRate +
                ", makerRate=" + makerRate +
                '}';
    }

    @Override
    public void copy(AccountConfig other) {
        this.priority = other.priority;
        this.takerRate = other.takerRate;
        this.makerRate = other.makerRate;
        this.properties.putAll(other.properties);
    }
}
