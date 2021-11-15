package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class AccountActor {
    private final static Logger logger = LoggerFactory.getLogger(AccountActor.class);

    private final AccountManager accountManager;

    public AccountActor(AccountManager accountManager) {
        this.accountManager = accountManager;
    }

    public double getTakerRate(Info0 info) {
        return accountManager.getAccount(info.getAccount()).getTakerRate();
    }

    public double getMakerRate(Info0 info) {
        return accountManager.getAccount(info.getAccount()).getMakerRate();
    }

    /**
     * query price
     */
    public DepthPrice getBidDepthPrice(Info0 info) throws StrategyException {
        return getBidDepthPrice(info, info.getInt("depth_qty", 79));
    }

    public DepthPrice getBidDepthPrice(Info0 info, int depthQty) throws StrategyException {
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                    new DepthPricePair(info.getName(), info.getSymbol(), depthQty)));
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
        Exchange ex = accountManager.getAccount(info.getAccount());
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                    new DepthPricePair(info.getName(), info.getSymbol(), depthQty)));
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