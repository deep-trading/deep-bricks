package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 期权功能定义，包括交割合约，永续合约
 * 有默认实现的接口方法，则表示可能不一定需要通过rest接口操作
 * default实现的方法一般建议通过websocket获取数据
 */
public interface FutureExApi extends ExApi {

    /**
     * 获取指标价格，标记价格
     * @param symbol exchange 的具体symbol名称
     * @return 返回净值价格，可能返回所有的净值价格，为统一接口，采用list
     * @throws ExApiException 执行失败
     */
    default List<NetValue> getNetValue(String symbol) throws ExApiException {
        throw new ExApiException("not implemented.");
    }

    /**
     * 获取仓位信息
     * @param symbol exchange 的具体symbol名称
     * @return 返回交易对的仓位信息，可能可以一次性返回所有交易对的仓位数据，所以采用list
     * @throws ExApiException 执行失败
     */
    default List<PositionValue> getPositionValue(String symbol) throws ExApiException {
        throw new ExApiException("not implemented.");
    }

    // 合约风险控制信息，包括仓位整体实际杠杆，仓位可用余额等信息，用于系统告警
    RiskLimitValue getRiskLimitValue() throws ExApiException;

    void updateRiskLimit(String symbol, int leverage) throws ExApiException;


    // 若为永续合约，则需要考虑资金费率
    /**
     * 获取某个时间点后的所有资金费用，此接口用于保存资金费用历史记录
     * 该接口返回值为List，有些平台可能返回全部资金费用
     * @param symbol 查询资金费用信息
     * @param lastTime 开始的时间
     * @return 对应的资金费用
     * @throws ExApiException 执行失败
     */
    List<FundingValue> getFundingValue(String symbol, long lastTime) throws ExApiException;

    /**
     * 获取最新下一个时间点的资金费率
     * 一般交易所为8小时收取一次资金费率，所以此处统一取8小时有效资金费率
     * @param symbol 交易所交易对名称
     * @return 返回对应的资金费率值
     * @throws ExApiException 执行失败
     */
    default double getFundingRate(String symbol) throws ExApiException {
        throw new ExApiException("not implemented.");
    }

    // 异步获取合约持仓信息接口
    default CompletableFuture<List<PositionValue>> asyncGetPositionValues() throws ExApiException {
        throw new ExApiException("not implemented");
    }

    // 永续合约账户风险信息接口
    default CompletableFuture<RiskLimitValue> asyncGetRiskLimitValue() throws ExApiException {
        throw new ExApiException("not implemented");
    }

    default CompletableFuture<Void> asyncUpdateLeverage(String symbol, int leverage) throws ExApiException {
        throw new ExApiException("not implemented");
    }

    default CompletableFuture<Void> asyncUpdateLimitValue(String symbol, int limit) throws ExApiException {
        throw new ExApiException("not implemented");
    }

    // 永续合约资金费率和资金费接口
    default CompletableFuture<Double> asyncGetFundingRate(String symbol) throws ExApiException {
        throw new ExApiException("not implemented");
    }

    default CompletableFuture<List<FundingValue>> asyncGetFundingValue(String symbol, long lastTime, long endTime)
            throws ExApiException {
        throw new ExApiException("not implemented");
    }
}
