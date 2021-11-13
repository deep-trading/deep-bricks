package org.eurekaka.bricks.common.exception;

public class InitializeException extends RuntimeException {

    public InitializeException(String message) {
        super(message);
    }

    public InitializeException(String message, Throwable cause) {
        super(message, cause);
    }
}
