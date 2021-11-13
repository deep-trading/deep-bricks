package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 定义 exchange 基本动作,接入 rest api 接口
 * 定义 spot，future，option，以及其他不同种类交易对，均需要的基础动作
 */
public interface ExApi {

    /**
     * 返回websocket连接认证需要的消息
     * @return login auth message
     * @throws ExApiException 执行失败
     */
    String getAuthMessage() throws ExApiException;

    /**
     * 获取交易所的交易对信息
     * @return 返回该交易所所有交易对信息
     * @throws ExApiException 执行失败
     */
    List<ExSymbol> getExchangeInfos() throws ExApiException;

    // get account balances, coins, futures or options
    List<AccountValue> getAccountValue() throws ExApiException;

    // make order
    String makeOrder(Order order) throws ExApiException;

    /**
     * 查询所有当前订单
     * @param symbol agent对应的有效交易对名称
     * @return 返回所有当前 open orders
     * @throws ExApiException 执行失败
     */
    default List<CurrentOrder> getCurrentOrders(String symbol) throws ExApiException {
        return getCurrentOrders(symbol, 0);
    }

    /**
     * 根据type查询对应方向的所有open orders
     * @param symbol 交易对名称
     * @param type 1代表买单，2代表卖单，0代表所有订单
     * @return 返回对应方向所有订单
     * @throws ExApiException 执行失败
     */
    List<CurrentOrder> getCurrentOrders(String symbol, int type) throws ExApiException;

    // 取消订单
    CurrentOrder cancelOrder(String symbol, String orderId) throws ExApiException;

    // 根据深度要求查询买单方向深度信息，websocket数据
//    DepthPrice getBidDepthPrice(DepthPrice depthPrice) throws ExApiException;

    // 根据深度要求查询卖单方向深度信息，websocket数据
//    DepthPrice getAskDepthPrice(DepthPrice depthPrice) throws ExApiException;

    /**
     * 账户内部转账
     * @throws ExApiException 执行失败
     */
    default void transferAsset(AssetTransfer transfer) throws ExApiException {}

    /**
     * 提币，只允许母账户实现该功能，子账户不支持提币
     * @throws ExApiException 执行失败
     */
    default void withdrawAsset(AssetTransfer transfer) throws ExApiException {}

    /**
     * 查询提币、充值记录
     * @throws ExApiException 执行失败
     */
    default List<AccountAssetRecord> getAssetRecords(AssetTransferHistory transferHistory) throws ExApiException {
        return null;
    }

    default List<KLineValue> getKLineValues(KLineValuePair kLineValuePair) throws ExApiException {
        return Collections.emptyList();
    }
}
