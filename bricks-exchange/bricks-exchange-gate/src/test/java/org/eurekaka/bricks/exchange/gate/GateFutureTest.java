package org.eurekaka.bricks.exchange.gate;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.model.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

public class GateFutureTest {
    public static void main(String[] args) throws Exception {
        Config config = ConfigFactory.load("deep/gate-secret.conf");
        AccountConfig accountConfig = new AccountConfig(0, "n1", 1, null,
                "org.eurekaka.bricks.exchange.gate.GateFutureListener",
                "org.eurekaka.bricks.exchange.gate.GateFutureApi",
                "wss://fx-ws.gateio.ws/v4/ws/usdt",
                "https://api.gateio.ws", config.getString("uid"),
                config.getString("key"), config.getString("secret"), true);

        // local test
        accountConfig.setProperty("http_proxy_host", "localhost");
        accountConfig.setProperty("http_proxy_port", "8123");

        Exchange exchange = new GateFuture(accountConfig);
        exchange.start();

        String name = "EOS_USDT";
        String symbol = "EOS_USDT";

        exchange.process(new ExAction<>(ExAction.ActionType.ADD_SYMBOL, new SymbolPair(name, symbol)));
        BlockingQueue<Notification> blockingQueue = new LinkedBlockingQueue<>();
        exchange.process(new ExAction<>(ExAction.ActionType.REGISTER_QUEUE, blockingQueue));

//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_FEES,
//                System.currentTimeMillis() - 8 * 3600000)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_RISK_LIMIT)));
//        System.out.println(exchange.process(new ExAction<>(ExAction.ActionType.GET_BALANCES)));
//
//        DepthPricePair depthPricePair = new DepthPricePair(name, symbol, 100);
//        SymbolPair symbolPair = new SymbolPair(name, symbol);
//
//        int count = 0;
//        while (count++ < 500) {
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

//        Order order = new Order("n1", name, symbol,
//                OrderSide.SELL, OrderType.MARKET, 4, 11, 6);
//        exchange.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER, order));
        Order order2 = new Order("n1", name, symbol,
                OrderSide.SELL, OrderType.LIMIT_IOC, 1, 5, 9);
        String clientOrderId = order2.getName() + "_" + System.currentTimeMillis();
        order2.setClientOrderId(clientOrderId);
        ExMessage<?> msg = exchange.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER_V2, order2));
        System.out.println("async make order: " + ((CompletableFuture<CurrentOrder>) msg.getData()).get());
        ActionPair pair = new ActionPair(name, symbol, clientOrderId);
        msg = exchange.process(new ExAction<>(ExAction.ActionType.GET_ORDER_V2, pair));
        System.out.println("async get order: " + ((CompletableFuture<CurrentOrder>) msg.getData()).get());
        msg = exchange.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER_V2, pair));
        System.out.println("async get orders: " + ((CompletableFuture<List<CurrentOrder>>) msg.getData()).get());
        msg = exchange.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER_V2, pair));
        System.out.println("async cancel order: " + ((CompletableFuture<Void>) msg.getData()).get());

        Thread.sleep(3000);
        for (Notification notification : blockingQueue) {
            System.out.println(notification);
        }

        exchange.stop();
    }
}
