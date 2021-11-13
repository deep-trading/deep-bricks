package org.eurekaka.bricks.market.stat;

import com.typesafe.config.Config;
import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.common.exception.StatException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.stat.Stat;
import org.eurekaka.bricks.server.store.FutureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PositionStatV2 implements Stat {
    private final static Logger logger = LoggerFactory.getLogger(PositionStatV2.class);

    private long nextTime;

    private final AccountManager accountManager;
    private final InfoState<Info0, ?> infoState;
    private final FutureStore store;

    private final double delta;
    private String maxPositionQuantityStr;
    private int maxPositionQuantity;

    public PositionStatV2(Config config, AccountManager accountManager,
                          InfoState<Info0, ?> infoState, FutureStore store) {
        this.accountManager = accountManager;
        this.infoState = infoState;
        this.store = store;

        this.delta = config.hasPath("stat_delta") ? config.getDouble("stat_delta") : 0.2D;

        this.nextTime = System.currentTimeMillis() / MINUTE * MINUTE + MINUTE;
    }

    @Override
    public void execute() throws StatException {
        List<Info0> infos = infoState.getInfos().stream()
                .filter(e -> e.getType() == 1).collect(Collectors.toList());

        Set<String> accounts1 = infoState.getInfos().stream()
                .filter(e -> e.getType() == 1)
                .map(Info0::getAccount).collect(Collectors.toSet());

        Set<String> accounts2 = infoState.getInfos().stream()
                .filter(e -> e.getType() == 2)
                .map(Info0::getAccount).collect(Collectors.toSet());

        List<PositionValue> positionValues1 = new ArrayList<>();
        for (String account : accounts1) {
            ExMessage<?> positionValueMsg = accountManager.getAccount(account)
                    .process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (positionValueMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                positionValues1.addAll((List<PositionValue>) positionValueMsg.getData());
            }
        }

        List<PositionValue> positionValues2 = new ArrayList<>();
        for (String account : accounts2) {
            ExMessage<?> positionValueMsg = accountManager.getAccount(account)
                    .process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (positionValueMsg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                positionValues2.addAll((List<PositionValue>) positionValueMsg.getData());
            }
        }

        // 存储position value 快照
        try {
            for (PositionValue positionValue : positionValues1) {
                store.storePositionValue(positionValue);
            }
            for (PositionValue positionValue : positionValues2) {
                store.storePositionValue(positionValue);
            }
        } catch (StoreException e) {
            throw new StatException("failed to store position values", e);
        }

        // 风险控制策略
        for (Info0 info : infos) {
            for (PositionValue position : positionValues1) {
                if (info.getName().equals(position.getName())) {
                    if (Math.abs(position.getQuantity()) / 1000 > info.getInt("max_position_quantity_1k", 10)) {
                        if (position.getQuantity() > 0) {
                            // 只允许卖合约
                            info.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.SELL.name());
                        } else {
                            info.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.BUY.name());
                        }
                    } else {
                        info.setProperty(Info0.ORDER_SIDE_KEY, OrderSide.ALL.name());
                    }
                }
            }
        }

        // name -> ex name -> position value
        Map<String, Map<String, Long>> hedgingPositions = new HashMap<>();
        for (PositionValue position : positionValues2) {
            if (!hedgingPositions.containsKey(position.getName())) {
                hedgingPositions.put(position.getName(), new HashMap<>());
            }
            Map<String, Long> hPosition = hedgingPositions.get(position.getName());
            hPosition.put(position.getAccount(), position.getQuantity());
        }

        updateFutureSide(hedgingPositions);
    }

    @Override
    public long getNextTime() {
        return nextTime;
    }

    @Override
    public void updateNextTime() {
        nextTime += MINUTE;
    }

    private void updateFutureSide(Map<String, Map<String, Long>> hedgerPositions) {
        hedgerPositions.forEach((future, hPos) -> {
            if (hPos.size() == 1) {
                return;
            }
            long p1 = 0, p2 = 0;
            for (Long p : hPos.values()) {
                p1 += p;
                p2 += Math.abs(p);
            }

            if (p1 != 0) {
                double t = p2 * 1.0 / Math.abs(p1) / delta;

                for (Map.Entry<String, Long> entry : hPos.entrySet()) {
                    String hedger = entry.getKey();
                    Long pos = entry.getValue();
                    try {
                        double left;
                        double right;
                        if (p1 > 0) {
                            left = p1 / t;
                            right = p1 - p1 / t;
                        } else {
                            left = p1 - p1 / t;
                            right = p1 / t;
                        }
                        left = Math.round(left);
                        right = Math.round(right);
                        OrderSide side;
                        if (pos < left) {
                            side = OrderSide.BUY;
                        } else if (pos > right) {
                            side = OrderSide.SELL;
                        } else {
                            side = OrderSide.ALL;
                        }
                        for (Info0 info : infoState.getInfos()) {
                            if (info.getAccount().equals(hedger) && info.getName().equals(future)) {
                                OrderSide infoSide = OrderSide.valueOf(info.getProperty("side", "ALL"));
                                if (!side.equals(infoSide)) {
                                    info.setProperty("side", side.name());
                                    infoState.updateInfo(info);
                                    logger.info("update {} {} with side: {}, pos: {}, left: {}, right: {}",
                                            hedger, future, side, pos, left, right);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.error("failed to update future side", e);
                    }
                }
            }
        });
    }
}
