package org.eurekaka.bricks.common.exception;

public class StrategyException extends Exception {

    public StrategyException(String message) {
        super(message);
    }

    public StrategyException(String message, Throwable cause) {
        super(message, cause);
    }
}
