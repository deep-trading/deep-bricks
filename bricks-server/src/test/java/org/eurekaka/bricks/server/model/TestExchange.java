package org.eurekaka.bricks.server.model;

import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.*;

public class TestExchange  implements Exchange {
    private AccountConfig accountConfig;

    public TestExchange(AccountConfig accountConfig) {
        this.accountConfig = accountConfig;
    }

    @Override
    public void start() throws ExchangeException {
        System.out.println("starting test exchange");
    }

    @Override
    public void stop() {
        System.out.println("stopping test exchange");
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public int getPriority() {
        return this.accountConfig.getPriority();
    }

    @Override
    public String getName() {
        return this.accountConfig.getName();
    }

    @Override
    public double getTakerRate() {
        return 0;
    }

    @Override
    public double getMakerRate() {
        return 0;
    }

    @Override
    public ExMessage<?> process(ExAction<?> action) {
        switch (action.getType()) {
            case GET_NET_VALUE: {
                SymbolPair pair = (SymbolPair) action.getData();
                return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                        new NetValue(pair.name, pair.symbol, 10));
            }
            case ADD_SYMBOL: {
                System.out.println("add symbol: " + action.getData());
                return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
            }
            case REMOVE_SYMBOL: {
                System.out.println("remove symbol: " + action.getData());
                return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
            }
            case REGISTER_QUEUE: {
                System.out.println("register queue");
                return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
            }
            default: return new ExMessage<>(ExMessage.ExMsgType.ERROR);
        }
    }
}
