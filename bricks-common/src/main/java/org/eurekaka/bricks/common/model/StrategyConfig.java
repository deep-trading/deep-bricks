package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class StrategyConfig implements Copyable<StrategyConfig> {
    public final static String GLOBAL_INFO_NAME = "global";

    private int id;

    private String name;

    private String clz;

    // 绑定某一组symbol info
    // symbol info含有账户信息，对应的资产信息等
    // 若为global或者null等字段，表示全局对冲
    @JsonProperty("info_name")
    private String infoName;
    // 优先级，启动时按照优先级排序
    private int priority;

    private final Map<String, String> properties;

    private boolean enabled;

    @JsonIgnore
    private final Map<String, Object> cache;

    public StrategyConfig() {
        this.properties = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
    }

    public StrategyConfig(int id, String name, String clz, String infoName,
                          boolean enabled, Map<String, String> properties) {
        this(id, name, clz, infoName, 1, enabled, properties);
    }

    public StrategyConfig(int id, String name, String clz, String infoName,
                          int priority, boolean enabled, Map<String, String> properties) {
        this.id = id;
        this.name = name;
        this.clz = clz;
        this.infoName = infoName;
        this.priority = priority;
        this.enabled = enabled;
        this.properties = new ConcurrentHashMap<>();
        if (properties != null) {
            this.properties.putAll(properties);
        }
        this.cache = new ConcurrentHashMap<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClz() {
        return clz;
    }

    public void setClz(String clz) {
        this.clz = clz;
    }

    public String getInfoName() {
        return infoName;
    }

    public void setInfoName(String infoName) {
        this.infoName = infoName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.cache.clear();
        this.properties.putAll(properties);
    }

    public void setProperty(String key, String value) {
        this.cache.remove(key);
        this.properties.put(key, value);
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String value) {
        return properties.getOrDefault(key, value);
    }

    public String getString(String key) {
        if (!properties.containsKey(key)) {
            throw new IllegalArgumentException("non key found: " + key);
        }
        return properties.get(key);
    }

    public boolean getBoolean(String key, boolean value) {
        try {
            if (!properties.containsKey(key)) {
                return value;
            }
            if (cache.containsKey(key)) {
                return (boolean) cache.get(key);
            }
            boolean v = Boolean.parseBoolean(properties.get(key));
            cache.put(key, v);
            return v;
        } catch (Throwable t) {
            return value;
        }
    }

    public int getInt(String key, int value) {
        try {
            if (!properties.containsKey(key)) {
                return value;
            }
            if (cache.containsKey(key)) {
                return (int) cache.get(key);
            }
            int v = Integer.parseInt(properties.get(key));
            cache.put(key, v);
            return v;
        } catch (Throwable t) {
            return value;
        }
    }

    public int getInt(String key) {
        if (!cache.containsKey(key)) {
            if (!properties.containsKey(key)) {
                throw new IllegalArgumentException("non required property found: " + key);
            }
            int v = Integer.parseInt(properties.get(key));
            cache.put(key, v);
            return v;
        }
        return (int) cache.get(key);
    }

    public long getLong(String key, long value) {
        try {
            if (!properties.containsKey(key)) {
                return value;
            }
            if (cache.containsKey(key)) {
                return (long) cache.get(key);
            }
            long v = Long.parseLong(properties.get(key));
            cache.put(key, v);
            return v;
        } catch (Throwable t) {
            return value;
        }
    }

    public double getDouble(String key, double value) {
        try {
            if (!properties.containsKey(key)) {
                return value;
            }
            if (cache.containsKey(key)) {
                return (double) cache.get(key);
            }
            double v = Double.parseDouble(properties.get(key));
            cache.put(key, v);
            return v;
        } catch (Throwable t) {
            return value;
        }
    }

    public double getDouble(String key) {
        if (!cache.containsKey(key)) {
            if (!properties.containsKey(key)) {
                throw new IllegalArgumentException("non required property found: " + key);
            }
            double v = Double.parseDouble(properties.get(key));
            cache.put(key, v);
            return v;
        }
        return (double) cache.get(key);
    }

    @Override
    public void copy(StrategyConfig other) {
        this.properties.putAll(other.properties);
        this.cache.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StrategyConfig)) return false;
        StrategyConfig config = (StrategyConfig) o;
        return id == config.id &&
                name.equals(config.name) &&
                enabled == config.enabled &&
                clz.equals(config.clz) &&
                Objects.equals(infoName, config.infoName) &&
                priority == config.priority &&
                properties.equals(config.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, clz, infoName, priority, properties);
    }

    @Override
    public String toString() {
        return "StrategyConfig{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", clz='" + clz + '\'' +
                ", infoName='" + infoName + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                ", properties=" + properties +
                '}';
    }
}
