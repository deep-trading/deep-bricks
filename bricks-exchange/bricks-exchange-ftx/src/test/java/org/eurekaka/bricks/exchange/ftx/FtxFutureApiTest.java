package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;
import org.eurekaka.bricks.common.util.Utils;

import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Properties;

public class FtxFutureApiTest {

    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.load(ClassLoader.getSystemResourceAsStream("deep/ftx-secret.conf"));

        AccountConfig accountConfig = new AccountConfig(0, "ftx", 1, "", "",
                "", "", "https://ftx.com", "test",
                properties.getProperty("key"), properties.getProperty("secret"), true);

//        accountConfig.setProperty("sub_account", "杠杠ETF对冲");
//        accountConfig.setProperty("sub_account", "test");
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");
        accountConfig.setProperty("http_request_timeout", "5000");

//        HttpClient httpClient = HttpClient.newHttpClient();
        HttpClient httpClient = HttpUtils.initializeHttpClient(accountConfig.getProperties());

        String name = "EOS_USDT";
        String symbol = "EOS-PERP";

        FutureExApi api = new FtxFutureApi(accountConfig, httpClient);

//        System.out.println(api.getExchangeInfos());
//        System.out.println(api.getAccountValue());
//        System.out.println(api.getPositionValue(null));
//        System.out.println(api.getRiskLimitValue());
//        api.updateRiskLimit(symbol, 10);
//        System.out.println(api.getRiskLimitValue());
//        System.out.println(api.getFundingValue(symbol, System.currentTimeMillis() - 3600000));
//        System.out.println(api.getFundingRate(symbol));

//        String id1 = api.makeOrder(new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT, 2, 0, 0));
//        System.out.println(id1);

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.MARKET, 2, 4.3, 9);
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
//        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, orderId));

        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, orderId));

//        for (CurrentOrder currentOrder : api.getCurrentOrders(symbol)) {
//            System.out.println(currentOrder);
//            api.cancelOrder(symbol, currentOrder.getId());
//        }

//        System.out.println(api.asyncGetAccountValues().get());
//        System.out.println(api.asyncGetPositionValues().get());

        // 4. 测试异步订单接口
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.MARKET, 1, 4.1, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT_GTX, 2, 3.41, 9));
        System.out.println("order: " + api.asyncCancelOrder(symbol, "EOS_USDT_1642577556308").get());
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.LIMIT_GTC, 1, 3.9, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.LIMIT_IOC, 1, 3.18, 9));

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

}
