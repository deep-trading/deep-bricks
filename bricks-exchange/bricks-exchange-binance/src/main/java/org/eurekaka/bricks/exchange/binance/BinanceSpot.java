package org.eurekaka.bricks.exchange.binance;

import org.eurekaka.bricks.api.AbstractExchange;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AccountStatus;

public class BinanceSpot extends AbstractExchange<AccountStatus, BinanceSpotApi> {

    public BinanceSpot(AccountConfig accountConfig) {
        super(accountConfig, new AccountStatus());
    }

}
