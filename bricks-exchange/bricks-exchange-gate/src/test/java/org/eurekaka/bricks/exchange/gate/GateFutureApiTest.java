package org.eurekaka.bricks.exchange.gate;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;

import java.net.http.HttpClient;

public class GateFutureApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/gate-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "e1", 1, null,
                null, null, null, "https://api.gateio.ws", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        HttpClient httpClient = HttpUtils.initializeHttpClient(accountConfig.getProperties());
        FutureExApi api = new GateFutureApi(accountConfig, httpClient);

        String name = "EOS_USDT";
        String symbol = "EOS_USDT";

        // 1. 测试查询接口数据
        System.out.println(api.asyncGetOrderBook(symbol, 100).get());
//        System.out.println(api.getAuthMessage());
//        System.out.println(api.getPositionValue(null));
//        System.out.println(api.getExchangeInfos());
//        System.out.println(api.getAccountValue());
//        System.out.println(api.getRiskLimitValue());
//        api.updateRiskLimit(symbol, 1000000);
//        System.out.println(api.getRiskLimitValue());

//        System.out.println(api.getFundingValue(symbol, System.currentTimeMillis() - 8 * 3600000));

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.SELL, OrderType.MARKET, 2, 3, 6);
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
//        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, orderId));

        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, "48063911594"));

//        for (CurrentOrder currentOrder : api.getCurrentOrders(symbol)) {
//            System.out.println(currentOrder);
//            api.cancelOrder(symbol, currentOrder.getId());
//        }

        // 4. 测试异步订单接口
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.MARKET, 1, 4.1, 9));
        testAsyncOrderApi(api, new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.LIMIT_GTX, 2, 3.48, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.BUY, OrderType.LIMIT_GTC, 2, 3.9, 9));
//        testAsyncOrderApi(api, new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.LIMIT_IOC, 2, 4.48, 9));

        HttpUtils.shutdownHttpClient(httpClient);
    }

    private static void testAsyncOrderApi(FutureExApi api, Order order) throws Exception {
        String clientOrderId = order.getName() + "_" + System.currentTimeMillis();
        order.setClientOrderId(clientOrderId);
        System.out.println("async make order: " + api.asyncMakeOrder(order).get());
        System.out.println("get orders: " + api.asyncGetCurrentOrders(order.getSymbol()).get());
        System.out.println("get order: " + api.asyncGetOrder(order.getSymbol(), clientOrderId).get());
        System.out.println("order: " + api.asyncCancelOrder(order.getSymbol(), clientOrderId).get());
    }

}
