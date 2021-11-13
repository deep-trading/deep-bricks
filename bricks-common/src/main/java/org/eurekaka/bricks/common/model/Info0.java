package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class Info0 extends Info<Info0> {
    // properties 系统内部通用的默认字段
    public final static String ORDER_SIDE_KEY = "side";
    public final static String ASSET_KEY = "asset";
    public final static String DEPTH_QTY_KEY = "depth_qty";
    public final static String HEDGING_INFO_KEY = "hedging_info";

    // 用于系统内交易对分组
    private int type;

    /**
     * 自定义配置，需要在rest接口内校验对应的配置是否正确
     * 分为可变与不可变两种类型
     *      _xx 为不可变对象
     *      xx 为可变字段
     */
    private final Map<String, String> properties;
    @JsonIgnore
    private final Map<String, Object> cache;

    public Info0() {
        this.properties = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
    }

    public Info0(int id, String name, String symbol, String account, int type,
                 double pricePrecision, double sizePrecision, boolean enabled,
                 Map<String, String> properties) {
        super(id, name, symbol, account, pricePrecision, sizePrecision, enabled);
        this.type = type;
        this.properties = new ConcurrentHashMap<>(properties);
        this.cache = new ConcurrentHashMap<>();
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public String getProperty(String key, String value) {
        return properties.getOrDefault(key, value);
    }

    public String getString(String key) {
        if (!properties.containsKey(key)) {
            throw new IllegalArgumentException("non info property found: " + key);
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

    public void setProperty(String key, String value) {
        this.cache.remove(key);
        this.properties.put(key, value);
    }

    public void setProperties(Map<String, String> properties) {
        this.cache.clear();
        this.properties.putAll(properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void copy(Info0 other) {
        super.copy(other);
        this.cache.clear();
        this.properties.putAll(other.properties);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Info0)) return false;
        if (!super.equals(o)) return false;
        Info0 info0 = (Info0) o;
        return type == info0.type && properties.equals(info0.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, properties);
    }

    @Override
    public String toString() {
        return "Info0{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", symbol='" + symbol + '\'' +
                ", account='" + account + '\'' +
                ", pricePrecision=" + pricePrecision +
                ", sizePrecision=" + sizePrecision +
                ", enabled=" + enabled +
                ", type=" + type +
                ", properties=" + properties +
                "} ";
    }
}
