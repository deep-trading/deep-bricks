package org.eurekaka.bricks.exchange.binance;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.StatisticsUtils;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;

public class BinanceFutureApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/binance-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                null, null, null, "https://fapi.binance.com", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        HttpClient httpClient = HttpUtils.initializeHttpClient(accountConfig.getProperties());
        FutureExApi api = new BinanceFutureApi(accountConfig, httpClient);

        String name = "EOS_USDT";
        String symbol = "EOSUSDT";

        // 1. 测试查询接口数据
//        System.out.println(api.asyncGetOrderBook(symbol, 20).get());
//        System.out.println(api.getAuthMessage());
//        System.out.println(api.getPositionValue(null));
//        System.out.println(api.getExchangeInfos());
//        System.out.println(api.getAccountValue());
//        System.out.println(api.getRiskLimitValue());
//        api.updateRiskLimit(symbol, 10);
//        System.out.println(api.getRiskLimitValue());

//        System.out.println(api.getFundingValue(symbol, System.currentTimeMillis() - 8 * 3600000));

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.LIMIT, 2, 3, 6);
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
//        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, "12746625430"));

        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, orderId));
//        for (CurrentOrder currentOrder : api.getCurrentOrders(symbol)) {
//            System.out.println(currentOrder);
//            api.cancelOrder(symbol, currentOrder.getId());
//        }

        // 4. 测试指标计算
        // 取过去30分钟指标
//        List<KLineValue> lineValues = api.getKLineValues(new KLineValuePair(name, symbol,
//                System.currentTimeMillis() - 1800000, System.currentTimeMillis(),
//                KLineInterval._1M, 30));
//        for (KLineValue value : lineValues) {
//            System.out.println(value);
//        }
//        System.out.println(StatisticsUtils.getSMA_RSI(lineValues, 14));
//        System.out.println(StatisticsUtils.getMeanAverage(lineValues, 6));
//        System.out.println(StatisticsUtils.getMeanAverage(lineValues, 26));

        // 5 async make order test
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.MARKET, 2, 4.1, 9));
        testAsyncOrderApi(api, new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.LIMIT_GTX, 2, 3.02, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT_GTC, 2, 4.1, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT_IOC, 2, 4.21, 9));
//        testAsyncCancellingOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT_GTX, 2, 4.01, 8));

        HttpUtils.shutdownHttpClient(httpClient);
    }

    private static void testAsyncOrderApi(FutureExApi api, Order order) throws Exception {
        String clientOrderId = order.getName() + "_" + System.currentTimeMillis();
        order.setClientOrderId(clientOrderId);
        System.out.println("async make order: " + api.asyncMakeOrder(order).get());
        System.out.println("get orders: " + api.asyncGetCurrentOrders(order.getSymbol()).get());
        System.out.println("get order: " + api.asyncGetOrder(order.getSymbol(), clientOrderId).get());
        System.out.println("order: " + api.asyncCancelOrder(order.getSymbol(), clientOrderId).get());
        System.out.println("order: " + api.asyncCancelOrder(order.getSymbol(), clientOrderId).get());
    }

    private static void testAsyncCancellingOrderApi(FutureExApi api, Order order) throws Exception {
        String clientOrderId = order.getName() + "_" + System.currentTimeMillis();
        order.setOrderId(clientOrderId);
        CompletableFuture<CurrentOrder> orderFuture = api.asyncMakeOrder(order);
//        Thread.sleep(3);
        orderFuture.cancel(true);
        System.out.println("order: " + api.asyncCancelOrder(order.getSymbol(), clientOrderId).get());
        System.out.println("get order: " + api.asyncGetOrder(order.getSymbol(), clientOrderId).get());
        Thread.sleep(1000);
        System.out.println("get orders: " + api.asyncGetCurrentOrders(order.getSymbol()).get());
    }
}
