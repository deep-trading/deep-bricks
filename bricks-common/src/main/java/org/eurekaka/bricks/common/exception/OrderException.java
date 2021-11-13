package org.eurekaka.bricks.common.exception;

public class OrderException extends Exception {

    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
