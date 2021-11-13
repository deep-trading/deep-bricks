package org.eurekaka.bricks.server.strategy;

import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.Strategy;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.eurekaka.bricks.server.BrickContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.eurekaka.bricks.common.model.ReportEvent.EventType.*;
import static org.eurekaka.bricks.common.model.ReportEvent.EventType.MONITOR_HEDGING_AVAILABLE_MARGIN;

/**
 * 风险告警
 */
public class RiskLimitStrategy implements Strategy {
    private final Logger logger = LoggerFactory.getLogger(RiskLimitStrategy.class);

    protected final BrickContext brickContext;
    protected final StrategyConfig strategyConfig;

    protected int type;
    protected double positionRatio;
    protected double positionLevel;
    protected double availableLevel;

    public RiskLimitStrategy(BrickContext brickContext,
                             StrategyConfig strategyConfig) {
        this.brickContext = brickContext;
        this.strategyConfig = strategyConfig;
    }

    @Override
    public void start() throws StrategyException {
        positionRatio = strategyConfig.getDouble("position_ratio", 0.8D);
        positionLevel = strategyConfig.getDouble("position_level", 5D);
        availableLevel = strategyConfig.getDouble("available_level", 0.1D);

        type = strategyConfig.getInt("type", 2);

        logger.info("risk limit strategy started");
    }

    @Override
    public void run() throws StrategyException {
        Map<String, RiskLimitValue> riskLimitValues = new HashMap<>();

        for (Exchange ex : brickContext.getAccountManager().getAccounts(type)) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_RISK_LIMIT));
            if (msg.getType() == ExMessage.ExMsgType.ERROR) {
                throw new StrategyException("failed to get risk limit value", (Throwable) msg.getData());
            }
            riskLimitValues.put(ex.getName(), (RiskLimitValue) msg.getData());
        }

        for (String account : riskLimitValues.keySet()) {
            RiskLimitValue riskLimitValue = riskLimitValues.get(account);

            double sum = 0;
            for (PositionRiskLimitValue position : riskLimitValue.positionRiskLimitValues) {
                if (position.limitValue > 0) {
                    double ratio = position.position * 1.0 / position.limitValue;
                    if (Math.abs(ratio) > positionRatio) {
                        String content = "too high risk limit ratio, account: " + account +
                                ",name: " + position.getName() + ",ratio: " + ratio;
                        logger.warn(content);
                        MonitorReporter.report( MONITOR_HEDGING_RISK_LIMIT.name() + account,
                                new ReportEvent(MONITOR_HEDGING_RISK_LIMIT,
                                        ReportEvent.EventLevel.WARNING, content));
                    }
                }
                sum += Math.abs(position.position);
            }

            if (riskLimitValue.totalBalance > 0) {
                double posLevel = sum / riskLimitValue.totalBalance;
                if (posLevel > positionLevel) {
                    String content = "position leverage too high, account: " +
                            account + ", position leverage: " + posLevel;
                    logger.warn(content);
                    MonitorReporter.report(MONITOR_HEDGING_LEVERAGE_LEVEL.name() + account,
                            new ReportEvent(MONITOR_HEDGING_LEVERAGE_LEVEL,
                                    ReportEvent.EventLevel.WARNING, content));
                }
                double availLevel = riskLimitValue.availableBalance / riskLimitValue.totalBalance;
                if (availLevel < availableLevel) {
                    String content = "available balance too low, account: " +
                            account + ", available level: " + availLevel;
                    MonitorReporter.report(MONITOR_HEDGING_AVAILABLE_MARGIN.name() + account,
                            new ReportEvent(MONITOR_HEDGING_AVAILABLE_MARGIN,
                                    ReportEvent.EventLevel.WARNING, content));
                }
            }

            processRiskLimitValue(account, riskLimitValue);
        }
    }

    @Override
    public long nextTime() {
        return System.currentTimeMillis() / 60000 * 60000 + 60000;
    }

    protected void processRiskLimitValue(String account, RiskLimitValue value) throws StrategyException {}
}
