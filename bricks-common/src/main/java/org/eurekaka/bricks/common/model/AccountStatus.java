package org.eurekaka.bricks.common.model;


import java.util.*;
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

    // websocket 接收的order book value，用于缓存最近一段时间的订单簿数据
    private final Map<String, LinkedList<OrderBookValue>> orderBookValues;

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
        this.orderBookValues = new ConcurrentHashMap<>();
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

    public Map<String, LinkedList<OrderBookValue>> getOrderBookValues() {
        return orderBookValues;
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

    public void updateOrderBookValue(String symbol, OrderBookValue orderBookValue) {
        // 更新原有的order book
        if (orderBookValues.containsKey(symbol)) {
            // 缓存order book value
            synchronized (orderBookValues.get(symbol)) {
                LinkedList<OrderBookValue> bookValues = orderBookValues.get(symbol);
                if (bookValues.isEmpty() || orderBookValue.lastUpdateId >= bookValues.getLast().lastUpdateId) {
                    bookValues.add(orderBookValue);
                }
            }

            if (!orderBookValue.bids.isEmpty()) {
                synchronized (bidOrderBooks.get(symbol)) {
                    updateOrderBookValuePair(bidOrderBooks.get(symbol), orderBookValue.bids);
                }
            }

            if (!orderBookValue.asks.isEmpty()) {
                synchronized (askOrderBooks.get(symbol)) {
                    updateOrderBookValuePair(askOrderBooks.get(symbol), orderBookValue.asks);
                }
            }
        }
    }

    // not thread safe
    public void buildOrderBookValue(String symbol, OrderBookValue orderBookValue) {
        // 根据snapshot, 更新tree map
        TreeMap<Double, Double> bidTreeMap = new TreeMap<>(Comparator.reverseOrder());
        TreeMap<Double, Double> askTreeMap = new TreeMap<>(Comparator.naturalOrder());

        updateOrderBookValuePair(bidTreeMap, orderBookValue.bids);
        updateOrderBookValuePair(askTreeMap, orderBookValue.asks);

        // 检查当前status内的order book values缓存，检查可用
        List<OrderBookValue> bookValues = this.orderBookValues.get(symbol);
        if (bookValues != null) {
            boolean found = false;
            for (OrderBookValue bookValue : bookValues) {
                if (orderBookValue.lastUpdateId <= bookValue.lastUpdateId) {
                    found = true;
                }
                if (found) {
                    updateOrderBookValuePair(bidTreeMap, bookValue.bids);
                    updateOrderBookValuePair(askTreeMap, bookValue.asks);
                }
            }
        }

        bidOrderBooks.put(symbol, bidTreeMap);
        askOrderBooks.put(symbol, askTreeMap);
    }

    private void updateOrderBookValuePair(Map<Double, Double> map, List<OrderBookValue.PriceSizePair> pairs) {
        for (OrderBookValue.PriceSizePair pair : pairs) {
            if (pair.size == 0) {
                map.remove(pair.price);
            } else {
                map.put(pair.price, pair.size);
            }
        }
    }

    public void updateBidOrderBookTicker(String symbol, double key, double value) {
        updateOrderBookTicker(bidOrderBooks, symbol, key, value);
    }

    public void updateAskOrderBookTicker(String symbol, double key, double value) {
        updateOrderBookTicker(askOrderBooks, symbol, key, value);
    }

    /**
     * 根据买一卖一更新订单簿
     * @param source 订单簿集合
     * @param symbol 交易对名称
     * @param key 当前挂单价格
     * @param value 当前挂单数量
     */
    private <K, T> void updateOrderBookTicker(Map<String, TreeMap<K, T>> source, String symbol, K key, T value) {
        if (source.containsKey(symbol)) {
            TreeMap<K, T> map = source.get(symbol);
            if (map.isEmpty()) {
                return;
            }
            if (map.comparator().compare(map.firstKey(), key) >= 0) {
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

}
