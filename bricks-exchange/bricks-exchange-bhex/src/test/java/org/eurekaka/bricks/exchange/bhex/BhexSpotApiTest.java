package org.eurekaka.bricks.exchange.bhex;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.ExApi;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.HttpUtils;

public class BhexSpotApiTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/bhex-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                null, null, null, "https://api.bhex.live",
                config.getString("uid"),
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        ExApi api = new BhexSpotApi(accountConfig,
                HttpUtils.initializeHttpClient(accountConfig.getProperties()));

        String name = "EOS_USDT";
        String symbol = "EOSUSDT";

        // 1. 测试查询接口数据
        System.out.println(api.getAccountValue());

        // 2. 测试正常下单撤单
        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.LIMIT_MAKER, 2, 4.1, 1);
//        String orderId = api.makeOrder(order);
//        order.setOrderId(orderId);
//        System.out.println("order id: " + orderId);
        System.out.println(api.getCurrentOrders(symbol));
//        System.out.println(api.cancelOrder(symbol, orderId));

//        // 3. 测试撤销已经撤销的订单
//        System.out.println(api.cancelOrder(symbol, orderId));

        // 转账
//        AccountConfig toAccountConfig1 = new AccountConfig(2, "n2", 1, null,
//                null, null, null, null, null,
//                null, null, true);
//        toAccountConfig1.setProperty("parent_account", "n1");
//        toAccountConfig1.setProperty("account_type", "1");
//        toAccountConfig1.setProperty("account_index", "1");
//        AssetTransfer transfer1 = new AssetTransfer(accountConfig, toAccountConfig1, "USDT", 2);
//        api.transferAsset(transfer1);

        // 提币
//        AccountConfig toAccountConfig2 = new AccountConfig(3, "n3", 1, null,
//                null, null, null, null, null,
//                null, null, true);
//        toAccountConfig2.setProperty("address", "TA1TNzacSFJ2AunUq2ASfns2AJ8MWZGYJh");
//        toAccountConfig2.setProperty("network", "TRC20");
//        AssetTransfer transfer2 = new AssetTransfer(accountConfig, toAccountConfig2, "USDT", 10);
//        api.withdrawAsset(transfer2);

        // 查看充提记录
//        AssetTransferHistory transferHistory = new AssetTransferHistory("n1", 2,
//                0L, 1621867534000L, 3);
//        System.out.println(api.getAssetRecords(transferHistory));
    }
}
