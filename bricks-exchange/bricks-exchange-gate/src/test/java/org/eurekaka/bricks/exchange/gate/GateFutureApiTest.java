package org.eurekaka.bricks.exchange.gate;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;

public class GateFutureApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/gate-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "e1", 1, null,
                null, null, null, "https://api.gateio.ws", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        FutureExApi api = new GateFutureApi(accountConfig,
                HttpUtils.initializeHttpClient(accountConfig.getProperties()));

        String name = "EOS_USDT";
        String symbol = "EOS_USDT";

        // 1. 测试查询接口数据
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
        System.out.println(api.cancelOrder(symbol, "48063911594"));

//        for (CurrentOrder currentOrder : api.getCurrentOrders(symbol)) {
//            System.out.println(currentOrder);
//            api.cancelOrder(symbol, currentOrder.getId());
//        }
    }

}
