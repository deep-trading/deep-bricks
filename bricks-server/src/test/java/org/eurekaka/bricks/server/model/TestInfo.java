package org.eurekaka.bricks.server.model;

import org.eurekaka.bricks.common.model.Info;

public class TestInfo extends Info<TestInfo> {

    public TestInfo(int id, String name, String symbol, String account,
                    double pricePrecision, double sizePrecision, boolean enabled) {
        super(id, name, symbol, account, pricePrecision, sizePrecision, enabled);
    }
}
