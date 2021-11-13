package org.eurekaka.bricks.exchange.deep;

import org.eurekaka.bricks.api.ExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeepSpotApi implements ExApi {
    private final static Logger logger = LoggerFactory.getLogger(DeepSpotApi.class);

    private final HttpClient httpClient;
    private final AccountConfig accountConfig;

    private final Set<String> marketAssets;
    private final double baseValue;
    private final Map<String, List<Order>> currentOrders;

    public DeepSpotApi(AccountConfig accountConfig, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.accountConfig = accountConfig;

        marketAssets = new HashSet<>();
        String symbolStr = accountConfig.getProperty("deep_market_assets");
        marketAssets.add("BTC3L");
        marketAssets.add("BTC3S");
        marketAssets.add("ETH3L");
        marketAssets.add("ETH3S");
        if (symbolStr != null) {
            for (String s : symbolStr.split(",")) {
                marketAssets.add(s.trim());
            }
        }
        baseValue = Double.parseDouble(accountConfig.getProperty(
                "deep_market_base", "300"));

        currentOrders = new ConcurrentHashMap<>();
    }

    @Override
    public String getAuthMessage() throws ExApiException {
        return null;
    }

    @Override
    public List<ExSymbol> getExchangeInfos() throws ExApiException {
        return new ArrayList<>();
    }

    @Override
    public List<AccountValue> getAccountValue() throws ExApiException {
        List<AccountValue> values = new ArrayList<>();
        for (String asset : marketAssets) {
            double total = System.nanoTime() % baseValue + baseValue;
            values.add(new AccountValue(asset, accountConfig.getName(), total, total));
        }
        double total = System.nanoTime() % baseValue + baseValue * 5;
        values.add(new AccountValue("USDT", accountConfig.getName(), total, total));
        return values;
    }

    @Override
    public String makeOrder(Order order) throws ExApiException {
        if (!currentOrders.containsKey(order.getSymbol())) {
            currentOrders.put(order.getSymbol(), new ArrayList<>());
        }
        order.setOrderId(order.getSymbol() + System.nanoTime());
        if (!order.getOrderType().equals(OrderType.LIMIT_MOCK)) {
            currentOrders.get(order.getSymbol()).add(order);
        }
        logger.debug("receive deep order: {}", order);
        return order.getOrderId();
    }

    @Override
    public List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException {
        List<CurrentOrder> orders = new ArrayList<>();
        if (currentOrders.containsKey(symbol)) {
            for (Order order : currentOrders.get(symbol)) {
                if (type == 1 && OrderSide.BUY.equals(order.getSide())) {
                    orders.add(new CurrentOrder(order.getOrderId(), order.getName(), order.getSymbol(),
                            OrderSide.BUY, OrderType.LIMIT, order.getSize(), order.getPrice(), 0));
                } else if (type == 2 && OrderSide.SELL.equals(order.getSide())) {
                    orders.add(new CurrentOrder(order.getOrderId(), order.getName(), order.getSymbol(),
                            OrderSide.SELL, OrderType.LIMIT, order.getSize(), order.getPrice(), 0));
                } else if (type == 0) {
                    orders.add(new CurrentOrder(order.getOrderId(), order.getName(), order.getSymbol(),
                            order.getSide(), OrderType.LIMIT, order.getSize(), order.getPrice(), 0));
                }
            }
        }
        return orders;
    }

    @Override
    public CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException {
        List<Order> orders = currentOrders.get(symbol);
        CurrentOrder currentOrder = null;

        Iterator<Order> iterator = orders.iterator();
        while (iterator.hasNext()) {
            Order o = iterator.next();
            if (o.getOrderId().equals(orderId)) {
                currentOrder = new CurrentOrder(orderId, o.getName(), o.getSymbol(),
                        o.getSide(), o.getOrderType(), o.getSize(), o.getPrice(), 0);
                logger.debug("remove order: {}", o);
                iterator.remove();
                break;
            }
        }

        return currentOrder;
    }
}
