package org.eurekaka.bricks.api;

import org.eurekaka.bricks.common.exception.ExApiException;
import org.eurekaka.bricks.common.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AbstractFutureExchange extends AbstractExchange<FutureAccountStatus, FutureExApi> {

    public AbstractFutureExchange(AccountConfig accountConfig, FutureAccountStatus accountStatus) {
        super(accountConfig, accountStatus);
    }

    @Override
    public ExMessage<?> process(ExAction<?> action) {
        ExMessage<?> result = super.process(action);
        if (!result.getType().equals(ExMessage.ExMsgType.UNKNOWN)) {
            return result;
        }
        try {
            switch (action.getType()) {
                case GET_POSITIONS:
                    return getPositions();
                case GET_POSITION:
                    return getPosition((SymbolPair) action.getData());

                case GET_RISK_LIMIT:
                    return getRiskLimit();
                case UPDATE_RISK_LIMIT:
                    return updateRiskLimit((RiskLimitPair) action.getData());

                case GET_FUNDING_RATE:
                    return getFundingRate((SymbolPair) action.getData());
                case GET_FUNDING_FEES:
                    return getFundingFees((Long) action.getData());

                default: return new ExMessage<>(ExMessage.ExMsgType.ERROR, "unknown action : " + action);
            }
        } catch (Exception e) {
            return new ExMessage<>(ExMessage.ExMsgType.ERROR, e);
        }
    }

    /**
     * 可能可以从accountStatus.positionValues 状态获取，也可能从api直接调用获取
     * @return 所有的position value，必须按照当前有效的symbols过滤
     * @throws ExApiException 执行失败
     */
    protected ExMessage<List<PositionValue>> getPositions() throws ExApiException {
        List<PositionValue> positionValues = new ArrayList<>();
        for (String symbol : accountStatus.getSymbols().keySet()) {
            if (accountStatus.getPositionValues().containsKey(symbol)) {
                String name = accountStatus.getSymbols().get(symbol);
                // 做一次拷贝
                PositionValue position = accountStatus.getPositionValues().get(symbol).copy();
                position.setName(name);
                updatePositionValue(position);
                positionValues.add(position);
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, positionValues);
    }

    protected ExMessage<PositionValue> getPosition(SymbolPair symbolPair) throws ExApiException {
        PositionValue value = accountStatus.getPositionValues().get(symbolPair.symbol);
        if (value != null) {
            PositionValue pos = value.copy();
            updatePositionValue(pos);
            pos.setName(symbolPair.name);
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT, pos);
        } else {
            return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                    new PositionValue(symbolPair.name, symbolPair.symbol,
                            accountConfig.getName(), 0, 0, 0,
                            0, 0, System.currentTimeMillis()));
        }
    }

    protected void updatePositionValue(PositionValue position) {
        // update position before return
    }

    protected ExMessage<RiskLimitValue> getRiskLimit() throws ExApiException {
        RiskLimitValue riskLimitValue = api.getRiskLimitValue();
        List<PositionRiskLimitValue> availablePositions = new ArrayList<>();
        for (PositionRiskLimitValue value : riskLimitValue.positionRiskLimitValues) {
            if (accountStatus.getSymbols().containsKey(value.symbol)) {
                value.setName(accountStatus.getSymbols().get(value.symbol));
                availablePositions.add(value);
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, new RiskLimitValue(
                riskLimitValue.totalBalance, riskLimitValue.availableBalance, availablePositions));
    }

    protected ExMessage<Double> getFundingRate(SymbolPair symbolPair) throws ExApiException {
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT,
                accountStatus.getFundingRates().getOrDefault(symbolPair.symbol, 0D));
    }

    protected ExMessage<Void> updateRiskLimit(RiskLimitPair riskLimitPair) throws ExApiException {
        this.api.updateRiskLimit(riskLimitPair.symbol, riskLimitPair.leverage);
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT);
    }

    protected ExMessage<List<FundingValue>> getFundingFees(long lastTime) throws ExApiException {
        // 返回全部的 funding value
        List<FundingValue> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : accountStatus.getSymbols().entrySet()) {
            for (FundingValue v : this.api.getFundingValue(entry.getKey(), lastTime)) {
                if (v.getSymbol().equals(entry.getKey())) {
                    v.setName(entry.getValue());
                    values.add(v);
                }
            }
        }
        return new ExMessage<>(ExMessage.ExMsgType.RIGHT, values);
    }

}
