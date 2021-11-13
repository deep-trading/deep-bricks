package org.eurekaka.bricks.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class JavaCompleteFutureTest {
    private final static Logger logger = LoggerFactory.getLogger(JavaCompleteFutureTest.class);

    public static void main(String[] args) {
        try {
            CompletableFuture.allOf(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                logger.info("f1 start: 0");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f1 stop: {}", end);
                return null;
            }), CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                logger.info("f2 start: 0");
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f2 stop: {}", end);
                throw new RuntimeException("runtime exception");
            }), CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                logger.info("f3 start: 0");
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f3 stop: {}", end);
                return start;
            }).thenAccept((a) -> {
                long start = a;
                logger.info("f3 start 1: {}", start);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f3 stop 1: {}", end);
            }), CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                logger.info("f4 start: 0");
                try {
                    Thread.sleep(800);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f4 stop: {}", end);
                return start;
            }).thenAcceptAsync((a) -> {
                long start = a;
                logger.info("f4 start 1: {}", start);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("interrupted");
                }
                long end = System.currentTimeMillis() - start;
                logger.info("f4 stop 1: {}", end);
            })).get();
        } catch (InterruptedException e) {
            logger.error("main interrupted");
        } catch (ExecutionException e) {
            logger.error("execute exception: ", e);
        }
    }
}
