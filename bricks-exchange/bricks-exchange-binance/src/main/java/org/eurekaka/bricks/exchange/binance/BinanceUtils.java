package org.eurekaka.bricks.exchange.binance;

import org.eurekaka.bricks.common.model.OrderStatus;
import org.eurekaka.bricks.common.model.OrderType;

public class BinanceUtils {

    public static OrderStatus getStatus(String status) {
        if (status == null) {
            return OrderStatus.NIL;
        }
        switch (status) {
            case "NEW":
                return OrderStatus.NEW;
            case "FILLED":
                return OrderStatus.FILLED;
            case "PARTIALLY_FILLED":
                return OrderStatus.PART_FILLED;
            case "CANCELLED":
            case "CANCELED":
                return OrderStatus.CANCELLED;
            case "REJECTED":
                return OrderStatus.REJECTED;
            case "EXPIRED":
                return OrderStatus.EXPIRED;
            default:
                return OrderStatus.NIL;
        }
    }

    public static OrderType getOrderType(String type, String timeInForce) {
        if ("MARKET".equals(type)) {
            return OrderType.MARKET;
        } else if ("LIMIT".equals(type)) {
            if ("IOC".equals(timeInForce)) {
                return OrderType.LIMIT_IOC;
            } else if ("GTC".equals(timeInForce)) {
                return OrderType.LIMIT_GTC;
            } else if ("GTX".equals(timeInForce)) {
                return OrderType.LIMIT_GTX;
            } else if ("FOK".equals(timeInForce)) {
                return OrderType.LIMIT_FOK;
            }
        }
        return OrderType.NONE;
    }

}
