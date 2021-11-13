package org.eurekaka.bricks.market.strategy;

import com.typesafe.config.Config;
import org.eurekaka.bricks.api.*;
import org.eurekaka.bricks.common.exception.OrderException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.market.model.StrategyStatus02;
import org.eurekaka.bricks.market.model.StrategyValue02;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.eurekaka.bricks.common.util.Utils.PRECISION;


/**
 * 定时检查策略交易对的仓位，对冲
 * 该策略检查strategy01的所有交易对仓位，并且定时运行对冲
 */
public class Strategy02 implements Strategy {
    private final static Logger logger = LoggerFactory.getLogger(Strategy02.class);

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;
    private final OrderExecutor orderExecutor;
    private final StrategyStatus02 strategyStatus;

    private final StrategyConfig strategyConfig;

    private final long hedgingInterval;
    private final long hedgingBaseInterval;
    private long nextHedgingTime;
    private long nextHedgingBaseTime;
    private final boolean debug;
    private Set<String> targetAccounts;

    public Strategy02(StrategyConfig strategyConfig,
                      AccountManager accountManager,
                      InfoState<Info0, ?> infoState,
                      OrderExecutor orderExecutor,
                      StrategyStatus02 strategyStatus) {
        this.accountManager = accountManager;
        this.infoState = infoState;
        this.orderExecutor = orderExecutor;
        this.strategyStatus = strategyStatus;

        this.strategyConfig = strategyConfig;

        hedgingInterval = strategyConfig.getLong("strategy_interval", 30000);
        hedgingBaseInterval = strategyConfig.getLong("strategy_base_interval", 3600000);
        this.debug = strategyConfig.getBoolean("debug", false);
    }

    @Override
    public void start() throws StrategyException {
        this.orderExecutor.start();

        this.nextHedgingTime = System.currentTimeMillis() /
                hedgingInterval * hedgingInterval + hedgingInterval;
        this.nextHedgingBaseTime = System.currentTimeMillis() /
                hedgingBaseInterval * hedgingBaseInterval + hedgingBaseInterval;

        String targetAccountStr = strategyConfig.getProperty("target_accounts");
        if (targetAccountStr == null) {
            throw new StrategyException("no target_accounts found");
        }
        targetAccounts = Arrays.stream(targetAccountStr.split(","))
                .map(String::trim).collect(Collectors.toSet());
        for (String targetAccount : targetAccounts) {
            if (accountManager.getAccount(targetAccount) == null) {
                throw new StrategyException("failed to get target account: " + targetAccount);
            }
        }


    }

    @Override
    public void run() throws StrategyException {
        long currentTime = System.currentTimeMillis();
        if (currentTime < nextHedgingTime && currentTime < nextHedgingBaseTime) {
            return;
        }

        List<Info0> strategyInfos = new ArrayList<>();
        List<Info0> hedgingInfos = new ArrayList<>();
        for (Info0 info : infoState.getInfos()) {
            if (targetAccounts.contains(info.getAccount())) {
                strategyInfos.add(info);
            } else {
                hedgingInfos.add(info);
            }
        }

        // 获取当前做市账户所有仓位信息，一个交易对仓位仅由一个账户管理
        List<PositionValue> positionValues = new ArrayList<>();
        for (String account : targetAccounts) {
            Exchange ex = accountManager.getAccount(account);
            ExMessage<?> positionValueMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (positionValueMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                positionValues.addAll((List<PositionValue>) positionValueMsg.getData());
            } else {
                throw new StrategyException("failed to get strategy position values: " + account,
                        (Throwable) positionValueMsg.getData());
            }
        }
        logger.info("do hedging, current positions: {}", positionValues);

        if (currentTime >= nextHedgingBaseTime) {
            // 执行全仓位对冲检查
            // 1. 获取对冲仓位信息
            Set<String> hedgingAccounts = hedgingInfos.stream()
                    .map(Info0::getAccount).collect(Collectors.toSet());

            Map<String, Double> hedgingPositionValues = new HashMap<>();
            for (String account : hedgingAccounts) {
                Exchange ex = accountManager.getAccount(account);
                ExMessage<?> positionValueMsg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
                if (positionValueMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                    List<PositionValue> positions = (List<PositionValue>) positionValueMsg.getData();
                    for (PositionValue pos : positions) {
                        if (hedgingPositionValues.containsKey(pos.getName())) {
                            hedgingPositionValues.put(pos.getName(),
                                    pos.getSize() + hedgingPositionValues.get(pos.getName()));
                        } else {
                            hedgingPositionValues.put(pos.getName(), pos.getSize());
                        }
                    }
                } else {
                    throw new StrategyException("failed to get hedging position values: " + account,
                            (Throwable) positionValueMsg.getData());
                }
            }
            logger.info("base hedging, positions: {}", hedgingPositionValues);
            for (Info0 info : strategyInfos) {
                for (PositionValue positionValue : positionValues) {
                    for (String posName : hedgingPositionValues.keySet()) {
                        if (info.getName().equals(positionValue.getName()) && info.getName().equals(posName)) {
                            double diffSize = positionValue.getSize() + hedgingPositionValues.get(posName);
                            diffSize = -diffSize;
                            double price = positionValue.getPrice();
                            long quantity = Math.round(diffSize * price);
                            if (!debug) {
                                orderExecutor.makeOrder(posName, quantity, Math.round(price * PRECISION));
                                // 更新当前仓位状态
                                strategyStatus.put(new StrategyValue02(info.getName(),
                                        positionValue.getSize(), nextHedgingBaseTime));
                            } else {
                                logger.info("base hedging, make order: {}, {}", posName, quantity);
                            }
                        }
                    }
                }
            }
            // 跳过增量对冲
            this.nextHedgingTime = currentTime /
                    hedgingInterval * hedgingInterval + hedgingInterval;
            this.nextHedgingBaseTime = currentTime /
                    hedgingBaseInterval * hedgingBaseInterval + hedgingBaseInterval;
        } else {
            // 执行增量对冲检查
            // 检查做市账户仓位interval前后变化差值，并且对冲等量
            for (Info0 info : strategyInfos) {
                for (PositionValue positionValue : positionValues) {
                    if (info.getName().equals(positionValue.getName()) &&
                            strategyStatus.get(info.getName()) != null) {
                        double diffSize = positionValue.getSize() - strategyStatus.get(info.getName()).value;
                        // 对冲相应的size
                        diffSize = -diffSize;
                        double price = positionValue.getPrice();
                        long quantity = Math.round(diffSize * price);
                        if (!debug) {
                            orderExecutor.makeOrder(info.getName(),
                                    quantity, Math.round(price * PRECISION));
                            // 更新当前仓位状态
                            strategyStatus.put(new StrategyValue02(info.getName(),
                                    positionValue.getSize(), nextHedgingTime));
                        } else {
                            logger.info("incremental hedging, make order: {}, {}", info.getName(), quantity);
                        }
                    }
                }
            }
            this.nextHedgingTime = currentTime /
                    hedgingInterval * hedgingInterval + hedgingInterval;
        }
    }
}
