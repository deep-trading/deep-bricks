package org.eurekaka.bricks.common.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.typesafe.config.Config;
import org.eurekaka.bricks.common.model.OrderSide;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Utils {

    /**
     * 此处设计统一的全局静态mapper对象，便于快速json操作
     * mapper是线程安全设计，json在序列化和反序列化过程中，应当没有锁的问题
     * 但针对不同的场景可能使用不同的序列化配置，在此基础上建议使用reader 和 writer
     */
    public static ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

//    public static ObjectMapper nonFinalMapper = mapper.copy()
//            .activateDefaultTyping(new LaissezFaireSubTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);

    public static final int PRECISION = 100000000;

    // 设置初始全局静态time zone
    private static ZoneOffset zoneOffset;
    // account的密钥加密存储数据库
    private static String authPassword;
    private static String authSalt;
    private static String appName;

    /**
     * 保存全局的一些工具变量
     * @param config 检查必须存在的配置项目
     */
    public static void checkConfig(Config config) {
        String zone = Objects.requireNonNull(config.getString("timezone"), "timezone is missing");
        zoneOffset = ZoneOffset.of(zone);
        authPassword = Objects.requireNonNull(config.getString("auth_password"),
                "auth_password is missing");
        authSalt = Objects.requireNonNull(config.getString("auth_salt"),
                "auth_salt is missing");
        appName = Objects.requireNonNull(config.getString("app_name"),
                "app_name is missing");
    }

    public static String getAuthPassword() {
        return authPassword;
    }

    public static String getAuthSalt() {
        return authSalt;
    }

    public static String getAppName() {
        return appName;
    }

    public static ZonedDateTime getDateTime() {
        assert zoneOffset != null;
        return ZonedDateTime.now(zoneOffset);
    }

    public static String formatTime(long time) {
        assert zoneOffset != null;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.of(zoneOffset.getId()))
                .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static String getAuthorization(String host, String secret) {
        long ts = System.currentTimeMillis();
        String token = md5Hex(host + ts + secret);
        return host + ":" + ts + ":" + token;
    }

    /**
     * initial hmac encryption instance
     * @param secret secret key
     * @param algo HmacSHA256, HmacSHA512
     * @return hmac instance
     */
    public static Mac initialHMac(String secret, String algo) {
        Mac sha_HMAC;
        try {
            sha_HMAC = Mac.getInstance(algo);
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes(), algo);
            sha_HMAC.init(secret_key);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("failed to create hmac " + algo, e);
        }
        return sha_HMAC;
    }

    public static byte[] sha512(String content) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("failed to do sha512 hash", e);
        }
        return md.digest(content.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] md5(String content) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("failed to do md5 hash", e);
        }
        return md.digest(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5Hex(String content) {
        return encodeHexString(md5(content));
    }

    private static final char[] DIGITS_LOWER =
            new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static char[] encodeHex(byte[] data) {
        int l = data.length;
        char[] out = new char[l << 1];
        int i = 0;

        for(int var5 = 0; i < l; ++i) {
            out[var5++] = DIGITS_LOWER[(240 & data[i]) >>> 4];
            out[var5++] = DIGITS_LOWER[15 & data[i]];
        }

        return out;
    }

    public static String encodeHexString(byte[] data) {
        return new String(encodeHex(data));
    }

    public static String maskKeySecret(String content) {
        int len = content.length();
        if (len < 16) {
            return "***";
        }
        return content.substring(0, 4) + "***" + content.substring(len - 4, len);
    }

    public static double roundPrecisionValue(double value) {
        return value > 1.0 ? 1.0 / value : Math.round(1.0 / value);
    }

    public static double floor(double price, double precision) {
        return Math.floor(price * precision) / precision;
    }

    public static double ceil(double price, double precision) {
        return Math.ceil(price * precision) / precision;
    }

    public static double round(double quantity, double precision) {
        return Math.round(quantity * precision) / precision;
    }

    public static boolean buyAllowed(OrderSide side) {
        return OrderSide.ALL.equals(side) || OrderSide.BUY.equals(side);
    }

    public static boolean sellAllowed(OrderSide side) {
        return OrderSide.ALL.equals(side) || OrderSide.SELL.equals(side);
    }

    /**
     * 根据买一卖一更新订单簿
     * @param source 订单簿集合
     * @param symbol 交易对名称
     * @param key 当前挂单价格
     * @param value 当前挂单数量
     */
    public static <K, T> void updateOrderBookTicker(Map<String, TreeMap<K, T>> source, String symbol, K key, T value) {
        if (source.containsKey(symbol)) {
            TreeMap<K, T> map = source.get(symbol);
            if (map.isEmpty()) {
                return;
            }
            if (map.comparator().compare(map.firstKey(), key) > 0) {
                return;
            }

            synchronized (source.get(symbol)) {
                Iterator<Map.Entry<K, T>> iterator = map.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<K, T> it = iterator.next();
                    if (map.comparator().compare(it.getKey(), key) < 0) {
//                        System.out.println("remove key: " + it.getKey() + ", current: " + key);
                        iterator.remove();
                    } else {
                        break;
                    }
                }
                if (!iterator.hasNext()) {
                    map.put(key, value);
                }
            }
        }
    }

    /**
     * 更新订单簿某个价格订单
     * @param source 订单簿集合
     * @param symbol 交易对名称
     * @param key 价格
     * @param value 数量
     */
    public static <K, T> void updateOrderBookEntry(Map<String, TreeMap<K, T>> source, String symbol, K key, T value) {
        if (source.containsKey(symbol)) {
            synchronized (source.get(symbol)) {
                source.get(symbol).put(key, value);
            }
        }
    }

    public static <K, T> void removeOrderBookEntry(Map<String, TreeMap<K, T>> source, String symbol, K key) {
        if (source.containsKey(symbol)) {
            synchronized (source.get(symbol)) {
                source.get(symbol).remove(key);
            }
        }
    }

}
