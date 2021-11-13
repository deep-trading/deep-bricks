package org.eurekaka.bricks.server;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public class MainApp {
    private final static Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Missing config file.");
            System.exit(-1);
        }
        logger.info("starting with config: {}", args[0]);
        Config config = ConfigFactory.load(args[0]);
        // 构建brick context
        BrickContext brickContext = createContext(config);
        // 启动context
        brickContext.start();
        // 启动所有策略
        brickContext.startStrategies();

        // start the rest server
        int port = config.getConfig("server").getInt("port");
        URI baseUri = UriBuilder.fromUri("http://0.0.0.0").port(port).build();
        HttpServer server = GrizzlyHttpServerFactory
                .createHttpServer(baseUri, brickContext.getResourceConfig(), false);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping server");
            brickContext.stop();
            server.shutdown(5000, TimeUnit.MILLISECONDS);
        }));
        try {
            server.start();
            Thread.currentThread().join();
        } catch (IOException | InterruptedException e) {
            logger.warn("interrupted server", e);
        }
        logger.info("stopped server.");
    }

    private static BrickContext createContext(Config config) throws InitializeException {
        String contextClz = "org.eurekaka.bricks.server.BrickContext";
        if (config.getConfig("server").hasPath("context")) {
            contextClz = config.getConfig("server").getString("context");
        }

        try {
            Class<?> clz = Class.forName(contextClz);
            return (BrickContext) clz.getConstructor(Config.class).newInstance(config);
        } catch (ClassNotFoundException | NoSuchMethodException |
                InstantiationException | IllegalAccessException |
                InvocationTargetException e) {
            throw new InitializeException("failed to create context", e);
        }
    }

}
