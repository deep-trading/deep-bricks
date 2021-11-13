package org.eurekaka.bricks.exchange.huobi;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.FutureExApi;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.Order;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderType;
import org.eurekaka.bricks.common.util.HttpUtils;

import java.util.Properties;

public class HuoFutureApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/huo-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                null, null, null, "https://api.hbdm.com", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        FutureExApi api = new HuoFutureApi(accountConfig,
                HttpUtils.initializeHttpClient(accountConfig.getProperties()));

        String name = "EOS_USDT";
        String symbol = "EOS-USDT";

//        System.out.println(api.getExchangeInfos());
//        System.out.println(api.getAccountValue());
//        System.out.println(api.getPositionValue(symbol));
//        System.out.println(api.getPositionValue(null));
//        System.out.println(api.getRiskLimitValue());
//        api.updateRiskLimit(symbol, 5);
//        System.out.println(api.getRiskLimitValue());
//        System.out.println(api.getFundingValue(symbol, System.currentTimeMillis() - 8 * 3600000));
        System.out.println(api.getPositionValue(null));

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.LIMIT, 2, 3.5, 7);
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
//        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, orderId));

        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, orderId));
    }

}
