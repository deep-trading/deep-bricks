package org.eurekaka.bricks.exchange.bhex;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.model.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BhexFutureTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/bhex-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                "org.eurekaka.bricks.exchange.bhex.BhexFutureListener",
                "org.eurekaka.bricks.exchange.bhex.BhexFutureApi",
                "wss://wsapi.bhex.com",
                "https://api.bhex.com", null,
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        Exchange exchange = new BhexFuture(accountConfig);
        exchange.start();

        String name = "EOS_USDT";
        String symbol = "EOS-SWAP-USDT";

        exchange.process(new ExAction<>(ExAction.ActionType.ADD_SYMBOL, new SymbolPair(name, symbol)));
        BlockingQueue<Notification> blockingQueue = new LinkedBlockingQueue<>();
        exchange.process(new ExAction<>(ExAction.ActionType.REGISTER_QUEUE, blockingQueue));

//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_FEES,
//                System.currentTimeMillis() - 8 * 3600000)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_RISK_LIMIT)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_BALANCES)));
        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS)));

        DepthPricePair depthPricePair = new DepthPricePair(name, symbol, 100);
        SymbolPair symbolPair = new SymbolPair(name, symbol);

//        int count = 0;
//        while (count++ < 500) {
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS)));
//            Thread.sleep(5000);
//        }

        Order order = new Order("n1", name, symbol,
                OrderSide.SELL, OrderType.MARKET, 1, 4, 8);
        exchange.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
        Thread.sleep(3000);

        exchange.stop();
    }
}
