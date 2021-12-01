package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class AccountActor {
    private final static Logger logger = LoggerFactory.getLogger(AccountActor.class);

    private final AccountManager accountManager;

    public AccountActor(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    public double getTakerRate(String account) {
        return accountManager.getAccount(account).getTakerRate();
    }

    public double getMakerRate(String account) {
        return accountManager.getAccount(account).getMakerRate();
    }

    /**
     * query price
     */
    public DepthPrice getBidDepthPrice(Info0 info) throws StrategyException {
        return getBidDepthPrice(info, info.getInt("depth_qty", 79));
    }

    public DepthPrice getBidDepthPrice(Info0 info, int depthQty) throws StrategyException {
        return getBidDepthPrice(info.getAccount(), info.getName(), info.getSymbol(), depthQty);
    }

    public DepthPrice getBidDepthPrice(String account, String name, String symbol, int depthQty) throws StrategyException {
        Exchange ex = accountManager.getAccount(account);
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                    new DepthPricePair(name, symbol, depthQty)));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get bid depth price", (Exception) msg.getData());
            }
            return (DepthPrice) msg.getData();
        }
        return null;
    }

    public DepthPrice getAskDepthPrice(Info0 info) throws StrategyException {
        return getAskDepthPrice(info, info.getInt("depth_qty", 79));
    }

    public DepthPrice getAskDepthPrice(Info0 info, int depthQty) throws StrategyException {
        return getAskDepthPrice(info.getAccount(), info.getName(), info.getSymbol(), depthQty);
    }

    public DepthPrice getAskDepthPrice(String account, String name, String symbol, int depthQty) throws StrategyException {
        Exchange ex = accountManager.getAccount(account);
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                    new DepthPricePair(name, symbol, depthQty)));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get ask depth price", (Exception) msg.getData());
            }
            return (DepthPrice) msg.getData();
        }
        return null;
    }

    public NetValue getNetValue(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_NET_VALUE,
                    new SymbolPair(info.getName(), info.getSymbol())));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get net value", (Exception) msg.getData());
            }
            return (NetValue) msg.getData();
        }
        return null;
    }

    public double getLastPrice(Info0 info) throws StrategyException {
        NetValue value = getNetValue(info);
        if (value.getPrice() == 0) {
            throw new StrategyException("last price is 0: " + value);
        }
        return value.getPrice() * 1.0 / PRECISION;
    }

    public List<KLineValue> getKlineValues(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_KLINE,
                    new KLineValuePair(info.getName(), info.getSymbol(), info.getInt("kline_size", 61))));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get net value", (Exception) msg.getData());
            }
            return (List<KLineValue>) msg.getData();
        }
        return null;
    }


    /**
     * order operations
     */
    public List<CurrentOrder> getCurrentOrders(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> currentOrdersMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                    new CurrentOrderPair(info.getName(), info.getSymbol(), 0)));
            if (currentOrdersMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                logger.error("failed to get current orders. name: {}, symbol: {}",
                        info.getName(), info.getSymbol(), (Exception) currentOrdersMsg.getData());
                throw new StrategyException("failed to get strategy started.", (Exception) currentOrdersMsg.getData());
            }
            return (List<CurrentOrder>) currentOrdersMsg.getData();
        }
        return Collections.emptyList();
    }

    public CurrentOrder cancelOrder(Info0 info, String orderId) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                    new CancelOrderPair(info.getName(), info.getSymbol(), orderId)));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to cancel order name: " +
                        info.getName() + ", order id: " + orderId, (Exception) msg.getData());
            }
            return (CurrentOrder) msg.getData();
        }
        return null;
    }

    public void cancelAllOrders(Info0 info) throws StrategyException {
        for (CurrentOrder currentOrder : getCurrentOrders(info)) {
            cancelOrder(info, currentOrder.getId());
        }
    }

    public String makeOrder(Order order) throws StrategyException {
        Exchange ex = accountManager.getAccount(order.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to make order: " + order, (Exception) msg.getData());
            }
            return (String) msg.getData();
        }
        return null;
    }

    public CompletableFuture<CurrentOrder> asyncMakeOrder(Order order) throws StrategyException {
        ExMessage<?> msg = accountManager.getAccount(order.getAccount())
                .process(new ExAction<>(ExAction.ActionType.MAKE_ORDER_V2, order));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new StrategyException("failed to async make order: " + order, (Exception) msg.getData());
        }
        return (CompletableFuture<CurrentOrder>) msg.getData();
    }

    public CompletableFuture<Void> asyncCancelOrder(Order order) throws StrategyException {
        return asyncCancelOrder(order.getAccount(), order.getName(), order.getSymbol(), order.getClientOrderId());
    }

    public CompletableFuture<Void> asyncCancelOrder(String account, String name,
                                                    String symbol, String clientOrderId) throws StrategyException {
        ActionPair pair = new ActionPair(name, symbol, clientOrderId);
        ExMessage<?> msg = accountManager.getAccount(account)
                .process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER_V2, pair));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new StrategyException("failed to async cancel order, account " + account +
                    ", name: " + name + ", client order id: " + clientOrderId, (Exception) msg.getData());
        }
        return (CompletableFuture<Void>) msg.getData();
    }

    public CompletableFuture<CurrentOrder> asyncGetOrder(Info0 info, String clientOrderId) throws StrategyException {
        return asyncGetOrder(info.getAccount(), info.getName(), info.getSymbol(), clientOrderId);
    }

    public CompletableFuture<CurrentOrder> asyncGetOrder(String account, String name,
                                                         String symbol, String clientOrderId) throws StrategyException {
        ActionPair pair = new ActionPair(name, symbol, clientOrderId);
        ExMessage<?> msg = accountManager.getAccount(account)
                .process(new ExAction<>(ExAction.ActionType.GET_ORDER_V2, pair));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new StrategyException("failed to async get order: " + pair, (Exception) msg.getData());
        }
        return (CompletableFuture<CurrentOrder>) msg.getData();
    }

    public CompletableFuture<List<CurrentOrder>> asyncGetCurrentOrders(Info0 info) throws StrategyException {
        ActionPair pair = new ActionPair(info.getName(), info.getSymbol());
        ExMessage<?> msg = accountManager.getAccount(info.getAccount())
                .process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER_V2, pair));
        if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
            throw new StrategyException("failed to async get order: " + pair, (Exception) msg.getData());
        }
        return (CompletableFuture<List<CurrentOrder>>) msg.getData();
    }


    // account balance


    /**
     * future operations
     */

    // positions
    public List<PositionValue> getPositions(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get account positions", (Exception) msg.getData());
            }
            return (List<PositionValue>) msg.getData();
        }
        return null;
    }

    public PositionValue getPosition(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                    new SymbolPair(info.getName(), info.getSymbol())));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get account position", (Exception) msg.getData());
            }
            return (PositionValue) msg.getData();
        }
        return null;
    }

    // funding rate
    public double getFundingRate(Info0 info) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE,
                    new SymbolPair(info.getName(), info.getSymbol())));
            if (msg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                throw new StrategyException("failed to get funding rate", (Exception) msg.getData());
            }
            return (double) msg.getData();
        }
        return 0D;
    }

}
