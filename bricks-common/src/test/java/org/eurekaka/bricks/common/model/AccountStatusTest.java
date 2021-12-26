package org.eurekaka.bricks.common.model;

import org.junit.Test;

import java.util.Comparator;
import java.util.TreeMap;

public class AccountStatusTest {

    @Test
    public void testUpdateOrderBookTicker() {
        String symbol = "s1";
        AccountStatus accountStatus = new AccountStatus();

        TreeMap<Double, Double> map = new TreeMap<>(Comparator.reverseOrder());
        accountStatus.getBidOrderBooks().put(symbol, map);

        map.put(3.451, 1D);
        map.put(3.452, 2D);
        map.put(3.448, 3D);
        map.put(3.443, 4D);
        System.out.println(map);
        accountStatus.updateBidOrderBookTicker(symbol, 3.45, 1);
        System.out.println(map);
    }
}
