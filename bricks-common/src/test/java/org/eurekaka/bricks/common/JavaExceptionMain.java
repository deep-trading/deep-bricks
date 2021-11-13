package org.eurekaka.bricks.common;

import org.eurekaka.bricks.common.exception.ExApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class JavaExceptionMain {
    private final static Logger logger = LoggerFactory.getLogger(JavaExceptionMain.class);

    public static void main(String[] args) throws Exception {
        try {
            try {
                throw new IOException("failed io");
            } catch (IOException e) {
                throw new ExApiException("failed exapi", e);
            }
        } catch (ExApiException e) {
            logger.error("now exception", e);
        }
    }

}
