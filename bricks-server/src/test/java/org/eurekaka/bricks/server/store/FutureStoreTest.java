package org.eurekaka.bricks.server.store;

import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.FundingValue;
import org.eurekaka.bricks.common.model.PositionValue;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class FutureStoreTest extends StoreTestBase {

    @Test
    public void testFutureStorePosition() throws StoreException {
        FutureStore store = new FutureStore();
        PositionValue positionValue = new PositionValue(
                "n1", "s1", "a1",
                1, 2, 2, 1, 1, 2);
        store.storePositionValue(positionValue);
        Assert.assertEquals(Collections.singletonList(positionValue),
                store.queryPositionValue("n1", 0, 10));
        Assert.assertEquals(Collections.singletonList(positionValue),
                store.queryPositionValueByTime(0));
    }

    @Test
    public void testFutureStoreFundingValue() throws StoreException {
        FutureStore store = new FutureStore();
        FundingValue fundingValue = new FundingValue(
                "n1", "s1", "a1", 1, 0.01, 2);
        store.storeFundingValue(fundingValue);
        Assert.assertEquals(fundingValue, store.queryFundingValueByTime(0).get(0));
        fundingValue.setTime(0);
        Assert.assertEquals(fundingValue, store.queryFundingValueFromTime(0).get(0));
    }

}
