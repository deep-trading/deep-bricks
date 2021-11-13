package org.eurekaka.bricks.common.exception;

public class ExApiException extends Exception {

    public ExApiException(String message) {
        super(message);
    }

    public ExApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
