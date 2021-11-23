package org.eurekaka.bricks.exchange.ftx;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.model.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class FtxFutureTest {

    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/ftx-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                "org.eurekaka.bricks.exchange.ftx.FtxFutureListener",
                "org.eurekaka.bricks.exchange.ftx.FtxFutureApi",
                "wss://ftx.com/ws",
                "https://ftx.com", config.getString("uid"),
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        Exchange exchange = new FtxFuture(accountConfig);
        exchange.start();

        String name = "EOS_USDT";
        String symbol = "EOS-PERP";

        exchange.process(new ExAction<>(ExAction.ActionType.ADD_SYMBOL, new SymbolPair(name, symbol)));
        BlockingQueue<TradeNotification> blockingQueue = new LinkedBlockingQueue<>();
        exchange.process(new ExAction<>(ExAction.ActionType.REGISTER_QUEUE, blockingQueue));

//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_FEES,
//                System.currentTimeMillis() - 3600000)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_RISK_LIMIT)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_BALANCES)));

        DepthPricePair depthPricePair = new DepthPricePair(name, symbol, 100);
        SymbolPair symbolPair = new SymbolPair(name, symbol);
//
//        int count = 0;
//        while (count++ < 500) {
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_MARK_USDT)));
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_NET_VALUES)));
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS)));
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_POSITION, symbolPair)));
//            System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_RATE, symbolPair)));
//            System.out.println(exchange.process(new ExAction<>(
//                    ExAction.ActionType.GET_BID_DEPTH_PRICE, depthPricePair)));
//            System.out.println(exchange.process(new ExAction<>(
//                    ExAction.ActionType.GET_ASK_DEPTH_PRICE, depthPricePair)));
//            Thread.sleep(500);
//        }

        Order order = new Order("n1", name, symbol,
                OrderSide.BUY, OrderType.MARKET, 1, 3.5, 6);
        exchange.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
        Thread.sleep(3000);
        for (Notification notification : blockingQueue) {
            System.out.println(notification);
        }

        exchange.stop();
    }

}
