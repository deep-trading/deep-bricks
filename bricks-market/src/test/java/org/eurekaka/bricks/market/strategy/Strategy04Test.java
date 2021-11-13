package org.eurekaka.bricks.market.strategy;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;

public class Strategy04Test {
    private Info0 info;
    private Exchange ex;
    private AccountManager accountManager;
    private StrategyConfig strategyConfig;
    private Strategy strategy;

    @Before
    public void setUp() throws Exception {
        info = new Info0(1, "n1", "s1", "e1",
                1, 1000, 100, true,
                Map.of("depth_qty", "100",
                       "min_order_quantity", "17"));

        strategyConfig = new StrategyConfig(1, "nn1",  "f1", "n1", true,
                Map.of("rand_order_quantity", "false",
                        "order_quantity", "60",
                        "bid_price_rate", "0.001",
                        "ask_price_rate", "0.001",
                        "bid_cancel_rate", "0.002",
                        "ask_cancel_rate", "0.002",
                        "profit_rate", "0.01",
                        "profit_callback_rate", "0.005",
                        "stop_loss_rate", "0.005"));

        ex = Mockito.mock(Exchange.class);
        Mockito.when(ex.getName()).thenReturn("e1");
        Mockito.when(ex.getMakerRate()).thenReturn(0.0005);
        Mockito.when(ex.getTakerRate()).thenReturn(0.001);
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_CURRENT_ORDER,
                new CurrentOrderPair("n1", "s1", 0))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, List.of()));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_NET_VALUE,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new NetValue("n1", "s1", "e1", 0)));

        accountManager = Mockito.mock(AccountManager.class);
        Mockito.when(accountManager.getAccount("e1")).thenReturn(ex);

        strategy = new Strategy04(strategyConfig, accountManager, info);
        strategy.start();
    }

    @After
    public void tearDown() throws Exception {
        strategy.stop();
    }

    @Test
    public void testBaseOrder() throws StrategyException {
        PositionValue position = new PositionValue("n1", "s1", "e1",
                0, 1, 0, 1, 0, 1L);
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, position));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_NET_VALUE,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new NetValue("n1", "s1", "e1", PRECISION)));

        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.98, 128, 131)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.02, 207, 203)));

        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                         61.35, 0.978, 60))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.SELL, OrderType.LIMIT,
                        58.71, 1.022, 60))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));

        // bidOrder = null, askOrder = null
        strategy.run();


        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.97, 175, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.99, 153, 155)));

        // 撤销id1
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id1", "n1", "s1",
                                OrderSide.BUY, OrderType.LIMIT, 61.35, 0.978, 0)));
        // 下新买单
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                        61.98, 0.968, 60))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id3"));

        // 撤销id2
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id2"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id2", "n1", "s1",
                                OrderSide.SELL, OrderType.LIMIT, 58.71, 1.022, 0)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.SELL, OrderType.LIMIT,
                        60.48, 0.992, 60))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id4"));

        // bidOrder = id1, askOrder = id2
        strategy.run();
    }


    @Test
    public void testCloseOrderLong() throws StrategyException {
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                        233, 1, 233, 1, 0, 1L)));

        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.002, 180, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.998, 180, 180)));

        // 此时未超过profit rate，不动
        strategy.run();

        // 卖一价超过profit rate
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.012, 182, 180)));
        // 触发askPeakPrice，不下单
        strategy.run();

        // 触发profit callback rate
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.005, 182, 180)));
        // 此时平仓下单
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.SELL, OrderType.LIMIT,
                        233, 1.005, 234))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));
        strategy.run();

        // 触发取消价格
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.002, 180, 180)));
        // 取消订单，重新下单
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id1", "n1", "s1",
                                OrderSide.SELL, OrderType.LIMIT, 233, 1.005, 0)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.SELL, OrderType.LIMIT,
                        233, 1.002, 233))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));
        strategy.run();
    }

    @Test
    public void testCloseOrderShort() throws StrategyException {
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                -233, 1, -233, 1, 0, 1L)));

        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.002, 180, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.998, 180, 180)));

        // 此时未超过profit rate，不动
        strategy.run();

        // 买一价超过profit rate
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.988, 178, 180)));
        // 触发askPeakPrice，不下单
        strategy.run();

        // 触发profit callback rate
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.995, 179, 180)));
        // 此时平仓下单
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                        233, 0.995, 232))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));
        strategy.run();

        // 触发取消价格
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.998, 180, 180)));
        // 取消订单，重新下单
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.CANCEL_ORDER,
                new CancelOrderPair("n1", "s1", "id1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new CurrentOrder("id1", "n1", "s1",
                                OrderSide.BUY, OrderType.LIMIT, 233, 0.995, 0)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                        233, 0.998, 233))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));
        strategy.run();
    }

    @Test
    public void testStopOrderLong() throws StrategyException {
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                233, 1, 233, 1, 0, 1L)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.993, 179, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 0.991, 179, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.SELL, OrderType.LIMIT,
                        233, 0.993, 231))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id1"));

        strategy.run();
    }

    @Test
    public void testStopOrderShort() throws StrategyException {
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_POSITION,
                new SymbolPair("n1", "s1"))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new PositionValue("n1", "s1", "e1",
                                -233, 1, -233, 1, 0, 1L)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_BID_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.008, 181, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.GET_ASK_DEPTH_PRICE,
                new DepthPricePair("n1", "s1", 100))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT,
                        new DepthPrice("n1", "s1", 1.009, 181, 180)));
        Mockito.when(ex.process(new ExAction<>(ExAction.ActionType.MAKE_ORDER,
                new Order("e1", "n1", "s1", OrderSide.BUY, OrderType.LIMIT,
                        233, 1.008, 235))))
                .thenReturn(new ExMessage(ExMessage.ExMsgType.RIGHT, "id2"));

        strategy.run();
    }
}
