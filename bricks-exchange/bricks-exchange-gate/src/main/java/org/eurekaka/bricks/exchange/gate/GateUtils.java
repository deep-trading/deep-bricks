package org.eurekaka.bricks.exchange.gate;

import org.eurekaka.bricks.common.model.OrderBookValue;
import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderStatus;
import org.eurekaka.bricks.common.model.OrderType;

import java.util.ArrayList;
import java.util.List;

public class GateUtils {

    public static OrderType getOrderType(double price, String tif) {
        if (price == 0 && "ioc".equals(tif)) {
            return OrderType.MARKET;
        } else if (price > 0) {
            if ("gtc".equals(tif)) {
                return OrderType.LIMIT_GTC;
            } else if ("poc".equals(tif)) {
                return OrderType.LIMIT_GTX;
            } else if ("ioc".equals(tif)) {
                return OrderType.LIMIT_IOC;
            }
        }
        return OrderType.NONE;
    }

    public static OrderStatus getStatus(String status, String finish_as, double filled) {
        if ("open".equals(status)) {
            return filled > 0 ? OrderStatus.PART_FILLED : OrderStatus.NEW;
        } else if ("finished".equals(status)) {
            if ("filled".equals(finish_as)) {
                return OrderStatus.FILLED;
            } else if ("cancelled".equals(finish_as)) {
                return OrderStatus.CANCELLED;
            } else if ("ioc".equals(finish_as)) {
                return OrderStatus.EXPIRED;
            }
        } else if ("cancelled".equals(status)) {
            return OrderStatus.CANCELLED;
        }
        return OrderStatus.NIL;
    }

    public static OrderSide getOrderSide(int size) {
        return size < 0 ? OrderSide.SELL : OrderSide.BUY;
    }

    public static String getClientOrderId(String text) {
        if (text != null && text.startsWith("t-")) {
            return text.substring(2);
        }
        return text;
    }
}
