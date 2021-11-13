package org.eurekaka.bricks.common.model;

import org.eurekaka.bricks.common.exception.StoreException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

public class InfoStateTest {

    @Test
    public void testSymInfoState() throws StoreException {
        InfoStore<TestInfo> store = Mockito.mock(InfoStore.class);

        TestInfo info = new TestInfo(1, "n1", "s1", "a1",
                100, 10, true);
        Mockito.when(store.query()).thenReturn(Collections.singletonList(info));
        Mockito.when(store.queryInfo(1)).thenReturn(info);
        InfoState<TestInfo, ?> state = new InfoState(store);

        Assert.assertEquals(1, state.getInfos().size());
        Assert.assertEquals(info, state.getInfo(1));
        Assert.assertEquals(info, state.queryInfo(1));

        info.setPricePrecision(1000);
        state.updateInfo(info);
        Assert.assertEquals(info, state.getInfo(1));

        state.disableInfo(1);
        Assert.assertEquals(0, state.getInfos().size());

        state.enableInfo(1);
        Assert.assertEquals(1, state.getInfos().size());

        state.disableInfo(1);
        state.deleteInfo(1);
    }

    static class TestInfo extends Info<TestInfo> {
        public TestInfo(int id, String name, String symbol, String account,
                        double pricePrecision, double sizePrecision, boolean enabled) {
            super(id, name, symbol, account, pricePrecision, sizePrecision, enabled);
        }
    }
}
