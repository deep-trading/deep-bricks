package org.eurekaka.bricks.exchange.ftx;

import org.eurekaka.bricks.common.model.OrderSide;
import org.eurekaka.bricks.common.model.OrderStatus;
import org.eurekaka.bricks.common.model.OrderType;

import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class FtxUtils {
    public final static String FTX_PING_BUFFER = "{\"op\": \"ping\"}";

    public static OrderStatus getOrderStatus(String status, double size, double filledSize) {
        if ("new".equals(status) || "open".equals(status)) {
            if (filledSize > 0) {
                return OrderStatus.PART_FILLED;
            }
            return OrderStatus.NEW;
        } else if ("closed".equals(status)) {
            if (filledSize == size) {
                return OrderStatus.FILLED;
            } else if (filledSize > 0) {
                return OrderStatus.PART_FILLED;
            } else {
                return OrderStatus.CANCELLED;
            }
        }
        return OrderStatus.NIL;
    }

    public static OrderType getOrderType(String orderType, boolean ioc, boolean postOnly) {
        if ("market".equals(orderType)) {
            return OrderType.MARKET;
        } else if ("limit".equals(orderType)) {
            if (ioc) {
                return OrderType.LIMIT_IOC;
            }
            if (postOnly) {
                return OrderType.LIMIT_GTX;
            }
            return OrderType.LIMIT_GTC;
        }
        return OrderType.NONE;
    }

    public static OrderSide getOrderSide(String side) {
        return "buy".equals(side) ? OrderSide.BUY : OrderSide.SELL;
    }

    public static long parseTimestampString(String timeString) {
        return ZonedDateTime.parse(timeString, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli();
    }

}
