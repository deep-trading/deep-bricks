package org.eurekaka.bricks.exchange.bhex;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;

public class BhexFutureApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/bhex-secret.conf");

        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                null, null, null, "https://api.bhex.com", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        BhexFutureApi api = new BhexFutureApi(accountConfig,
                HttpUtils.initializeHttpClient(accountConfig.getProperties()));

        String name = "EOS_USDT";
        String symbol = "EOS-SWAP-USDT";

//        System.out.println(api.getExchangeInfos());
        System.out.println(api.getAccountValue());
        System.out.println(api.getPositionValue(null));
        System.out.println(api.getRiskLimitValue());
//        String listenKey = api.getListenKey();
//        System.out.println(listenKey);
//        api.keepListenKey(listenKey);

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.MARKET, 1, 4, 6);
//        order.setId(System.currentTimeMillis());
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
//        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, orderId));

//        Thread.sleep(3000);
        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, orderId));
//        for (CurrentOrder currentOrder : api.getCurrentOrders(symbol)) {
//            System.out.println(currentOrder);
//            api.cancelOrder(symbol, currentOrder.getId());
//        }
    }

}
