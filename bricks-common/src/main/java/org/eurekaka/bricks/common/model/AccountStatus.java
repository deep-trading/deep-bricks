package org.eurekaka.bricks.common.model;


import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储基础状态数据
 */
public class AccountStatus {

    // ping-pong测试时间
    // 定时向服务端发送ping消息，接收到该pong消息返回时，更新此时间
    private long lastPongTime;

    // 系统内部与交易所之间的symbol名称映射，
    // 保证系统使用统一的交易对名称格式
    // symbol -> name
    private final Map<String, String> symbols;

    /**
     * symbol -> net value
     */
    private final Map<String, NetValue> netValues;

    // k line ticker 数据
    private final Map<String, List<KLineValue>> klineValues;

    // 大堆，first entry key 价格最高
    private final Map<String, TreeMap<Double, Double>> bidOrderBooks;

    // 小堆，first entry key 价格最低
    private final Map<String, TreeMap<Double, Double>> askOrderBooks;

    // mark usdt，转换统一计价单位，平台交易对可能以USDT计价，也可能以BUSD计价
    private double markUsdt;

    // queue 若设置可以直接推送消息到外部，必须通过set设置，否则不生效
    private Queue<Notification> notificationQueue;

    private final Map<String, AccountValue> balances;

    public AccountStatus() {
        this.symbols = new ConcurrentHashMap<>();
        this.netValues = new ConcurrentHashMap<>();
        this.bidOrderBooks = new ConcurrentHashMap<>();
        this.askOrderBooks = new ConcurrentHashMap<>();
        this.markUsdt = 1D;
        this.balances = new ConcurrentHashMap<>();
        this.klineValues = new ConcurrentHashMap<>();
    }

    public Map<String, String> getSymbols() {
        return symbols;
    }

    public Map<String, NetValue> getNetValues() {
        return netValues;
    }

    public Map<String, TreeMap<Double, Double>> getBidOrderBooks() {
        return bidOrderBooks;
    }

    public Map<String, TreeMap<Double, Double>> getAskOrderBooks() {
        return askOrderBooks;
    }

    public Queue<Notification> getNotificationQueue() {
        return notificationQueue;
    }

    public void setNotificationQueue(Queue<Notification> notificationQueue) {
        this.notificationQueue = notificationQueue;
    }

    public long getLastPongTime() {
        return lastPongTime;
    }

    public void updateLastPongTime() {
        this.lastPongTime = System.currentTimeMillis();
    }

    public void setMarkUsdt(double markUsdt) {
        // 防止markUsdt被设置为0
        this.markUsdt = markUsdt;
    }

    public double getMarkUsdt() {
        return markUsdt == 0D ? 1D : markUsdt;
    }

    public Map<String, AccountValue> getBalances() {
        return balances;
    }

    public Map<String, List<KLineValue>> getKlineValues() {
        return klineValues;
    }
}
