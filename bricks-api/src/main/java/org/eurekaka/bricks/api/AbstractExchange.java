package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

import static org.eurekaka.bricks.common.model.ReportEvent.EventType.HEDGING_AGENT_FAILED;

/**
 * 连接websocket，需要定时发送ping消息，否则会被服务端断开连接
 *
 * 设计时，考虑接收到的websocket数据与rest接口查询数据，需要将数据对象映射成系统内部统一使用的名称
 * 即 symbol -> name，再返回数据，此时要求symbol必须是目标平台唯一标识
 * 而在查询数据时，主动提供 (name, symbol)对，避免双向映射查询
 *
 * @param <A>
 * @param <B>
 */
public class AbstractExchange<A extends AccountStatus, B extends ExApi> implements Exchange {
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());

    protected A accountStatus;

    protected AccountConfig accountConfig;
    protected HttpClient httpClient;
    protected WebSocket webSocket;

    protected B api;

    protected WebSocket.Listener listener;

    // max seconds since last pong message received
    protected int httpLostTimeout;
    protected int httpPingInterval;

    // 默认的ping消息体
    private final ByteBuffer PING_BUFFER = ByteBuffer.wrap("ping".getBytes());

    private final boolean fakeOrder;

    // 定时器，检查websocket是否连接，如果断开则重新连接
    // 同时需要定时从服务端rest接口拉取数据
    protected ScheduledExecutorService webSocketMonitor;

    protected KLineInterval klineInterval;

    protected boolean enableKlineSub;

    public AbstractExchange(AccountConfig accountConfig, A accountStatus) {
        this.accountConfig = accountConfig;
        this.accountStatus = accountStatus;

        this.httpClient = HttpUtils.initializeHttpClient(accountConfig.getProperties());
        // 初始化action
        this.api = ClzUtils.createExApi(accountConfig.getApiClz(), accountConfig, httpClient);

        // listener 从 class reflection 获取，
        // 可以保证，所有的对象都在此处能够初始化，避免非初始化对象在 super class内
        // 若account config websocket 为null，则不启动websocket部分的功能
        if (accountConfig.getWebsocket() != null) {
            this.listener = ClzUtils.createListener(accountConfig.getListenerClz(),
                    accountConfig, accountStatus, api);

            this.httpLostTimeout = Integer.parseInt(accountConfig
                    .getProperty("http_lost_timeout", "15000"));

            this.httpPingInterval = Integer.parseInt(accountConfig
                    .getProperty("http_ping_interval", "3000"));
        }

        this.fakeOrder = Boolean.parseBoolean(accountConfig
                .getProperty("fake_order", "false"));

        enableKlineSub = Boolean.parseBoolean(accountConfig.getProperty(
                "enable_kline", "false"));
        this.klineInterval = KLineInterval.getKLineInterval(accountConfig
                .getProperty("kline_interval", "1m"));
    }

    @Override
    public void start() throws ExchangeException {
        if (accountConfig.getWebsocket() != null) {
            // 创建websocket连接
            this.webSocket = HttpUtils.createWebSocket(accountConfig, httpClient, listener);

            this.accountStatus.updateLastPongTime();

            // auth login here
            try {
                String authMsg = api.getAuthMessage();
                if (authMsg != null) {
                    webSocket.sendText(api.getAuthMessage(), true);
                }
            } catch (ExApiException e) {
                throw new ExchangeException("failed to send auth message", e);
            }

            startWebSocketMonitor();
        }

        postStart();

        logger.info("started exchange: {}", getName());
    }

    protected void postStart() throws ExchangeException {}

    protected void startWebSocketMonitor() {
        if (this.webSocketMonitor == null) {
            this.webSocketMonitor = Executors.newScheduledThreadPool(1);
            this.webSocketMonitor.scheduleWithFixedDelay(new WebSocketTimer(),
                    httpPingInterval, httpPingInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        preStop();

        if (accountConfig.getWebsocket() != null) {
            try {
                webSocket.sendClose(1000, "i am closing normally.")
                        .get(200, TimeUnit.MILLISECONDS);
                webSocketMonitor.shutdown();
                webSocketMonitor.awaitTermination(200, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.warn("close websocket abnormally", e);
            }
            webSocket.abort();
        }
        HttpUtils.shutdownHttpClient(httpClient);
        logger.info("stopped exchange: {}", getName());
    }

    protected void preStop() {}

    @Override
    public boolean isAlive() {
        return accountConfig.getWebsocket() == null ||
                webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed() &&
                System.currentTimeMillis() - accountStatus.getLastPongTime() < httpLostTimeout;
    }

    @Override
    public int getPriority() {
        return this.accountConfig.getPriority();
    }

    @Override
    public String getName() {
        return this.accountConfig.getName();
    }

    @Override
    public double getTakerRate() {
        return this.accountConfig.getTakerRate();
    }

    @Override
    public double getMakerRate() {
        return this.accountConfig.getMakerRate();
    }

    @Override
    public ExMessage<?> process(ExAction<?> action) {
        try {
            switch (action.getType()) {
                // websocket
                case ADD_SYMBOL:
                    return addSymbol((SymbolPair) action.getData());
                case REMOVE_SYMBOL:
                    return removeSymbol((SymbolPair) action.getData());
                case REGISTER_QUEUE:
                    return registerQueue((Queue<Notification>) action.getData());

                case GET_SYMBOLS:
                    return getSymbols();
                case GET_KLINE:
                    return getKLineValues((KLineValuePair) action.getData());

                case GET_NET_VALUE:
                    return getNetValue((SymbolPair) action.getData());
                case GET_NET_VALUES:
                    return getNetValues();
                case GET_BALANCES:
                    return getBalances();
                case GET_BALANCE:
                    return getBalance((String) action.getData());

                case MAKE_ORDER:
                    return makeOrder((Order) action.getData());
                case MAKE_ORDER_V2:
                    return makeOrderV2((Order) action.getData());

                case CANCEL_ORDER:
                    return cancelOrder((CancelOrderPair) action.getData());
                case CANCEL_ORDER_V2:
                    return cancelOrderV2((ActionPair) action.getData());

                case GET_CURRENT_ORDER:
                    return getCurrentOrders((CurrentOrderPair) action.getData());
                case GET_ORDER_V2:
                    return getOrderV2((ActionPair) action.getData());
                case GET_CURRENT_ORDER_V2:
                    return getCurrentOrdersV2((ActionPair) action.getData());

                case GET_BID_DEPTH_PRICE:
                    return getBidDepthPrice((DepthPricePair) action.getData());
                case GET_ASK_DEPTH_PRICE:
                    return getAskDepthPrice((DepthPricePair) action.getData());

                case GET_MARK_USDT:
                    return new ExMessage<>(ExMessage.ExMsgType.RIGHT, accountStatus.getMarkUsdt());

                case TRANSFER_ASSET:
                    return transferAsset((AssetTransfer) action.getData());
                case WITHDRAW_ASSET:
                    return withdrawAsset((AssetTransfer) action.getData());
                case GET_ASSET_RECORDS:
                    return getAssetRecords((AssetTransferHistory) action.getData());

                default: return new ExMessage<>(ExMessage.ExMsgType.UNKNOWN);
            }
        } catch (Exception e) {
            return new ExMessage<>(ExMessage.ExMsgType.ERROR, e);
        }
    }

    /**
     * 可以由exchange自定义ping buffer
     * 若发送的不是ping，而是普通text，可override该方法即可
     */
    protected void sendPingBuffer() {
        this.webSocket.sendPing(PING_BUFFER);
    }

    private ExMessage<Void> addSymbol(SymbolPair symbolPair) throws ExApiException {
        accountStatus.getSymbols().put(symbolPair.symbol, symbolPair.name);
        try {
            if (!accountStatus.getAskOrderBooks().containsKey(symbolPair.symbol)) {
                accountStatus.getAskOrderBooks().put(symbolPair.symbol, new TreeMap<>());
            }
            if (!accountStatus.getBidOrderBooks().containsKey(symbolPair.symbol)) {
                accountStatus.getBidOrderBooks().put(symbolPair.symbol,
                        new TreeMap<>(Comparator.reverseOrder()));
            }
            if (!accountStatus.getKlineValues().containsKey(symbolPair.symbol) && enableKlineSub) {
                // initial kline data
                int limit = Integer.parseInt(accountConfig.getProperty("kline_limit", "99"));
                long stopTime = System.currentTimeMillis();
                long startTime = stopTime - klineInterval.interval * 1000L * limit;
                KLineValuePair pair = new KLineValuePair(symbolPair.name, symbolPair.symbol,
                        startTime, stopTime, klineInterval, limit);
                List<KLineValue> kLineValues = api.getKLineValues(pair);
                accountStatus.getKlineValues().put(symbolPair.symbol, kLineValues);
            }

            if (accountConfig.getWebsocket() != null) {
                sendSub(symbolPair.symbol);
            }
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
        } catch (Throwable t) {
            throw new ExApiException("failed to add symbol: " + symbolPair.symbol, t);
        }
    }

    protected void sendSub(String symbol) throws ExApiException {
//        this.webSocket.sendText("", true);
    }

    private ExMessage<Void> removeSymbol(SymbolPair symbolPair) throws ExApiException {
        if (accountStatus.getSymbols().containsKey(symbolPair.symbol)) {
            if (accountConfig.getWebsocket() != null) {
                sendUnsub(symbolPair.symbol);
            }
            this.accountStatus.getBidOrderBooks().remove(symbolPair.symbol);
            this.accountStatus.getAskOrderBooks().remove(symbolPair.symbol);
            this.accountStatus.getKlineValues().remove(symbolPair.symbol);
            accountStatus.getSymbols().remove(symbolPair.symbol);
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
    }


    protected void sendUnsub(String symbol) throws ExApiException {}

    /**
     * 注册queue，便于主动向外部推送消息
     * @param queue linkedblockingqueue，消息队列
     */
    private ExMessage<Void> registerQueue(Queue<Notification> queue) throws ExApiException {
        this.accountStatus.setNotificationQueue(queue);
        postRegisterQueue();
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
    }

    /**
     * 发送消息订阅
     */
    protected void postRegisterQueue() throws ExApiException {}

    protected ExMessage<List<ExSymbol>> getSymbols() throws ExApiException {
        List<ExSymbol> exSymbols = api.getExchangeInfos();
        // 映射实际内部统一名称 symbol -> name，同时完成过滤
        for (ExSymbol exSymbol : exSymbols) {
            if (accountStatus.getSymbols().containsKey(exSymbol.symbol)) {
                exSymbol.setName(accountStatus.getSymbols().get(exSymbol.symbol));
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, exSymbols);
    }

    protected ExMessage<List<KLineValue>> getKLineValues(KLineValuePair pair) throws ExApiException {
        List<KLineValue> lineValues = new ArrayList<>();
        List<KLineValue> values = accountStatus.getKlineValues().get(pair.symbol);
        if (values != null) {
            int start = 0;
            if (values.size() >= pair.limit) {
                start = values.size() - pair.limit;
            }
            for (int i = start; i < values.size(); i++) {
                lineValues.add(values.get(i));
            }
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, lineValues);
        }
        return new ExMessage<>(ExMessage.ExMsgType.ERROR);
    }


    /**
     * 获取单个交易对的净值
     * @param symbolPair 交易对
     * @return 单个交易对净值
     * @throws ExApiException 查询失败
     */
    protected ExMessage<NetValue> getNetValue(SymbolPair symbolPair) throws ExApiException {
        NetValue value = accountStatus.getNetValues().get(symbolPair.symbol);
        if (value == null) {
            return new ExMessage<>(ExMessage.ExMsgType.ERROR);
        }
        value.setName(symbolPair.name);
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, value);
    }

    /**
     * 查询所有已注册交易对的净值
     * @return 返回所有已注册交易对的净值
     * @throws ExApiException 查询失败
     */
    protected ExMessage<List<NetValue>> getNetValues() throws ExApiException {
        List<NetValue> netValues = new ArrayList<>();
        // 返回所有的 net values
        for (String symbol : accountStatus.getSymbols().keySet()) {
            if (accountStatus.getNetValues().containsKey(symbol)) {
                String name = accountStatus.getSymbols().get(symbol);
                NetValue value = accountStatus.getNetValues().get(symbol);
                value.setName(name);
                netValues.add(value);
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, netValues);
    }

    protected ExMessage<List<AccountValue>> getBalances() throws ExApiException {
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, api.getAccountValue());
    }

    protected ExMessage<AccountValue> getBalance(String asset) throws ExApiException {
        AccountValue value = accountStatus.getBalances().get(asset);
        if (value != null) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                    new AccountValue(asset, value.account, value.totalBalance,
                            value.walletBalance, value.availableBalance));
        }
        return new ExMessage<>(ExMessage.ExMsgType.ERROR);
    }

    protected ExMessage<String> makeOrder(Order order) throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, "fake-" + order.getId());
        }
        String orderId = this.api.makeOrder(updateOrder(order));
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, orderId);
    }

    protected ExMessage<CompletableFuture<CurrentOrder>> makeOrderV2(Order order) throws ExApiException {
        if (order.getOrderId() == null) {
            order.setOrderId(order.getName() + ":" + System.nanoTime());
        }

        if (fakeOrder) {
            CurrentOrder currentOrder = new CurrentOrder(order.getOrderId(), order.getName(), order.getSymbol(),
                    order.getSide(), order.getOrderType(), order.getSize(), order.getPrice(), 0);
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, CompletableFuture.completedFuture(currentOrder));
        }

        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, api.asyncMakeOrder(order));
    }

    /**
     * 可在此处更改实际下单参数，例如平多平空等
     * @param order 待下单order
     * @return 更新后的order
     */
    protected Order updateOrder(Order order) {
        return order;
    }

    protected ExMessage<CurrentOrder> cancelOrder(CancelOrderPair cancelOrderPair) throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, new CurrentOrder(
                    cancelOrderPair.orderId, cancelOrderPair.name, cancelOrderPair.symbol,
                    OrderSide.NONE, OrderType.LIMIT, 0, 0, 0));
        }

        CurrentOrder currentOrder = api.cancelOrder(cancelOrderPair.symbol, cancelOrderPair.orderId);
        if (currentOrder == null) {
            throw new ExApiException("cancel order not found, name: " + cancelOrderPair.name +
                    ", symbol: " + cancelOrderPair.symbol + ", order id: " + cancelOrderPair.orderId);
        }
        currentOrder.setName(cancelOrderPair.name);
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, currentOrder);
    }

    protected ExMessage<CompletableFuture<Void>> cancelOrderV2(ActionPair pair) throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, CompletableFuture.completedFuture(null));
        }

        return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                api.asyncCancelOrder(pair.symbol, pair.getOrderId()));
    }


    protected ExMessage<List<CurrentOrder>> getCurrentOrders(CurrentOrderPair currentOrderPair) throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, Collections.emptyList());
        }

        List<CurrentOrder> currentOrders = api.getCurrentOrders(currentOrderPair.symbol, currentOrderPair.type);
        for (CurrentOrder currentOrder : currentOrders) {
            currentOrder.setName(currentOrderPair.name);
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, currentOrders);
    }

    protected ExMessage<CompletableFuture<List<CurrentOrder>>> getCurrentOrdersV2(ActionPair pair)
            throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                    CompletableFuture.completedFuture(Collections.emptyList()));
        }

        return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                api.asyncGetCurrentOrders(pair.symbol).thenApply(currentOrders -> {
                    for (CurrentOrder currentOrder : currentOrders) {
                        currentOrder.setName(pair.name);
                    }
                    return currentOrders;
                }));
    }

    protected ExMessage<CompletableFuture<CurrentOrder>> getOrderV2(ActionPair pair)
            throws ExApiException {
        if (fakeOrder) {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, CompletableFuture.completedFuture(null));
        }

        return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                api.asyncGetOrder(pair.symbol, pair.getOrderId()).thenApply(order -> {
                    order.setName(pair.name);
                    return order;
                }));
    }


    protected ExMessage<DepthPrice> getBidDepthPrice(DepthPricePair depthPricePair) throws ExApiException {
        if (accountStatus.getBidOrderBooks().containsKey(depthPricePair.symbol)) {
            TreeMap<Double, Double> depths = accountStatus.getBidOrderBooks().get(depthPricePair.symbol);
            int sum1 = 0;
            double sum2 = 0;
            for (Map.Entry<Double, Double> entry : depths.entrySet()) {
                double price = entry.getKey();
                int quantity = (int) Math.floor(price * entry.getValue());
                sum1 += quantity;
                sum2 += entry.getValue();
                if (sum1 >= depthPricePair.depthQty) {
                    return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                            new DepthPrice(depthPricePair.name, depthPricePair.symbol, price, sum1, sum2));
                }
            }
        }
        logger.warn("{} no enough depth: {}", depthPricePair.symbol,
                accountStatus.getBidOrderBooks().get(depthPricePair.symbol));
        return new ExMessage<>(ExMessage.ExMsgType.ERROR);
    }

    protected ExMessage<DepthPrice> getAskDepthPrice(DepthPricePair depthPricePair) {
        if (accountStatus.getAskOrderBooks().containsKey(depthPricePair.symbol)) {
            TreeMap<Double, Double> depths = accountStatus.getAskOrderBooks().get(depthPricePair.symbol);
            int sum1 = 0;
            double sum2 = 0;
            for (Map.Entry<Double, Double> entry : depths.entrySet()) {
                double price = entry.getKey();
                int quantity = (int) Math.floor(price * entry.getValue());
                sum1 += quantity;
                sum2 += entry.getValue();
                if (sum1 >= depthPricePair.depthQty) {
                    return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                            new DepthPrice(depthPricePair.name, depthPricePair.symbol, price, sum1, sum2));
                }
            }
        }
        logger.warn("{} no enough depth: {}", depthPricePair.symbol,
                accountStatus.getAskOrderBooks().get(depthPricePair.symbol));
        return new ExMessage<>(ExMessage.ExMsgType.ERROR);
    }

    protected ExMessage<Void> transferAsset(AssetTransfer transfer) throws ExApiException {
        api.transferAsset(transfer);
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
    }

    protected ExMessage<Void> withdrawAsset(AssetTransfer transfer) throws ExApiException {
        api.withdrawAsset(transfer);
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
    }

    protected ExMessage<List<AccountAssetRecord>> getAssetRecords(
            AssetTransferHistory transferHistory) throws ExApiException {
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, api.getAssetRecords(transferHistory));
    }

    private class WebSocketTimer implements Runnable {

        @Override
        public void run() {
            try {
                if (isAlive()) {
                    sendPingBuffer();
                } else {
                    logger.warn("{} exchange account is dead.", getName());
                    if (webSocket != null) {
                        webSocket.abort();
                    }
                    start();
                    // initial the symbols subscription
                    for (String symbol : accountStatus.getSymbols().keySet()) {
                        sendSub(symbol);
                    }
                    logger.info("reconnect to exchange account: {}", getName());
                }
                if (!isAlive()) {
                    logger.error("failed to restart exchange account: {}", getName());
                    MonitorReporter.report(HEDGING_AGENT_FAILED.name(),
                            new ReportEvent(HEDGING_AGENT_FAILED, ReportEvent.EventLevel.SERIOUS,
                                    "exchange agent dead: " + getName()));
                }
            } catch (Exception e) {
                logger.error("failed to check websocket alive", e);
            }
        }
    }


}
