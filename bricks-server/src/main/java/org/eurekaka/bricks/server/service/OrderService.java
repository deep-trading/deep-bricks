package org.eurekaka.bricks.server.service;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.store.ExOrderStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class OrderService {
    private final static Logger logger = LoggerFactory.getLogger(OrderService.class);

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;
    private final ExOrderStore orderStore;

    public OrderService(BrickContext brickContext) {
        this(brickContext, new ExOrderStore());
    }

    public OrderService(BrickContext brickContext, ExOrderStore orderStore) {
        this.accountManager = brickContext.getAccountManager();
        this.infoState = brickContext.getInfoState();
        this.orderStore = orderStore;
    }

    public void makeMarketOrder(String account, String name, double size) throws ServiceException {
        if (size == 0) {
            throw new ServiceException("make size 0 order.");
        }
        Info0 info = findInfo(account, name);
        Exchange ex = accountManager.getAccount(account);
        OrderSide side = OrderSide.BUY;
        if (size < 0) {
            side = OrderSide.SELL;
            size = -size;
        }
        Order order = new Order(account, name, info.getSymbol(), side,
                OrderType.MARKET, size, 0, 0);
        ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new ServiceException("failed to make order", (Exception) msg.getData());
        }
    }

    public void makeLimitOrder(String account, String name,
                               double size, double price) throws ServiceException {
        if (size == 0) {
            throw new ServiceException("make size 0 order.");
        }
        Info0 info = findInfo(account, name);
        Exchange ex = accountManager.getAccount(account);
        OrderSide side = OrderSide.BUY;
        if (size < 0) {
            side = OrderSide.SELL;
            size = -size;
        }
        Order order = new Order(account, name, info.getSymbol(), side,
                OrderType.LIMIT, size, price, Math.round(size * price));
        ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new ServiceException("failed to make order", (Exception) msg.getData());
        }
    }

    public List<CurrentOrder> getCurrentOrders(String account, String name) throws ServiceException {
        Info0 info = findInfo(account, name);
        Exchange ex = accountManager.getAccount(account);
        ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER_V2,
                new ActionPair(name, info.getSymbol())));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new ServiceException("failed to get current orders", (Exception) msg.getData());
        }
        try {
            return ((CompletableFuture<List<CurrentOrder>>) msg.getData()).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ServiceException("failed to get current orders", e);
        }
    }

    public CurrentOrder cancelOrder(String account, String name, String orderId) throws ServiceException {
        Info0 info = findInfo(account, name);
        Exchange ex = accountManager.getAccount(account);
        ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair(name, info.getSymbol(), orderId)));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new ServiceException("failed to cancel order", (Exception) msg.getData());
        }
        return (CurrentOrder) msg.getData();
    }

    public List<TradeNotification> getHistoryOrders(String account, String name,
                                                    long start, long stop, int limit) throws ServiceException {
        long currentTime = System.currentTimeMillis();
        if (start > currentTime || currentTime - start > 3 * 86400 * 1000) {
            start = currentTime - 3 * 86400 * 1000;
        }
        if (stop < start) {
            stop = currentTime;
        }
        if (limit == 0) {
            limit = 20;
        }
        try {
            return orderStore.queryHistoryOrders(account, name, start, stop, limit);
        } catch (StoreException e) {
            throw new ServiceException("failed to query history orders", e);
        }
    }


    private Info0 findInfo(String account, String name) throws ServiceException {

        List<Info0> infos = null;
        try {
            infos = infoState.queryAllInfos().stream()
                    .filter(e -> e.getName().equals(name) && e.getAccount().equals(account))
                    .collect(Collectors.toList());
        } catch (StoreException e) {
            throw new ServiceException("no account and name matched");
        }
        if (infos.size() != 1) {
            throw new ServiceException("no matched info found: " + infos);
        }
        return infos.get(0);
    }

}
