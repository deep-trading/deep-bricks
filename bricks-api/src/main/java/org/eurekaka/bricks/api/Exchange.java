package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.model.ExAction;
import org.eurekaka.bricks.common.model.ExMessage;

/**
 * exchange指交易所，根据账户配置启动交易服务
 */
public interface Exchange {

    /**
     * 启动exchange
     * 考虑websocket断开后，需要重新连接新的websocket对象
     * 所以在此处调用 newWebSocketBuilder().buildAsync()`
     * @throws ExchangeException 交易失败
     */
    void start() throws ExchangeException;

    /**
     * 停止websocket连接，所有异常记录日志即可
     */
    void stop();

    /**
     * 定期发送ping
     * 查看当前websocket是否已经断开链接
     * 启动scheduled线程，定时检查websocket连接
     * 向websocket server发送定时ping消息，更新本地接收到的pong time，监测前后间隔
     * @return true if connected
     */
    boolean isAlive();

    /**
     * 获取账户的优先级
     * 在查询某个交易所账户的市场信息时，通过优先级配置最有效信息
     * @return 账户优先级
     */
    int getPriority();

    /**
     * 获取账户定义名称
     * @return 账户名称，必须唯一
     */
    String getName();

    /**
     * 获取账户taker的费率
     * @return taker 市价单费率
     */
    double getTakerRate();

    /**
     * 获取账户的maker费率
     * @return maker 限价单费率
     */
    double getMakerRate();

    ExMessage<?> process(ExAction<?> action);
}
