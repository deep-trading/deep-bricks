package org.eurekaka.bricks.exchange.deep;

import org.eurekaka.bricks.api.AbstractExchange;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AccountStatus;

public class DeepSpot extends AbstractExchange<AccountStatus, DeepSpotApi> {

    public DeepSpot(AccountConfig accountConfig) {
        super(accountConfig, new AccountStatus());
    }

}
