package org.eurekaka.bricks.exchange.bhex;

import org.eurekaka.bricks.api.AbstractExchange;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AccountStatus;

public class BhexSpot extends AbstractExchange<AccountStatus, BhexSpotApi> {
    public BhexSpot(AccountConfig accountConfig) {
        super(accountConfig, new AccountStatus());
    }

}
