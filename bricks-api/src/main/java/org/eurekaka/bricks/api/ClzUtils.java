package org.eurekaka.bricks.api;

import org.eurekaka.bricks.api.ExApi;
import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.model.AccountConfig;
import org.eurekaka.bricks.common.model.AccountStatus;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;

/**
 * 创建定义class 反射方法
 */
public class ClzUtils {

    public static <A extends AccountStatus, B extends ExApi> WebSocket.Listener createListener(
            String listenerClz, AccountConfig accountConfig,
            A accountStatus, B api) {
        try {
            Class<?> clz = Class.forName(listenerClz);
            Constructor<?> constructor = clz.getConstructors()[0];
            return (WebSocket.Listener) constructor.newInstance(accountConfig, accountStatus, api);
        } catch (ClassNotFoundException | InstantiationException |
                IllegalAccessException | InvocationTargetException e) {
            throw new InitializeException("failed to create websocket listener " + listenerClz, e);
        }
    }

    public static <T extends ExApi> T createExApi(String apiClz, AccountConfig accountConfig,
                                                  HttpClient httpClient) {
        try {
            Class<?> clz = Class.forName(apiClz);
            Constructor<?> constructor = clz.getConstructor(AccountConfig.class, HttpClient.class);
            return (T) constructor.newInstance(accountConfig, httpClient);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                IllegalAccessException | InvocationTargetException e) {
            throw new InitializeException("failed to create exchange api " + apiClz, e);
        }
    }

    public static Exchange createExchange(String clzName, AccountConfig accountConfig) throws ExchangeException {
        try {
            Class<?> clz = Class.forName(clzName);
            Constructor<?> constructor = clz.getConstructor(AccountConfig.class);
            return (Exchange) constructor.newInstance(accountConfig);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                IllegalAccessException | InvocationTargetException e) {
            throw new ExchangeException("failed to create exchange " + clzName, e);
        }
    }


}
