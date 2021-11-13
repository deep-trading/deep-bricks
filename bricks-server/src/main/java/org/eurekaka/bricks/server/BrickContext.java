package org.eurekaka.bricks.server;

import com.typesafe.config.Config;
import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.api.StrategyFactory;
import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StrategyException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.MonitorReporter;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.listener.HistoryOrderListener;
import org.eurekaka.bricks.server.manager.*;
import org.eurekaka.bricks.server.rest.AppResource;
import org.eurekaka.bricks.server.state.StrategyConfigState;
import org.eurekaka.bricks.server.store.*;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class BrickContext {
    protected final Logger logger = LoggerFactory.getLogger(getClass().getName());

    public final Config config;
    protected StrategyConfigState strategyConfigState;
    protected InfoState<Info0, ?> infoState;
    protected AccountAssetState assetState;
    protected AccountConfigState accountConfigState;

    protected AccountManagerImpl accountManager;
    protected StrategyManager strategyManager;

    public BrickContext(Config config) {
        this.config = config;

        // 初始化全局配置，例如时区等
        Utils.checkConfig(config.getConfig("server"));
        // 初始化database
        DatabaseStore.setDataSource(DatabaseStore.getDatabaseSource(config.getConfig("database")));
        // 初始化告警器
        MonitorReporter.start(config.getConfig("monitor"));
    }

    /**
     * 便于测试
     */
    public BrickContext(Config config, StrategyConfigState strategyConfigState,
                        InfoState<Info0, ?> infoState, AccountAssetState assetState,
                        AccountConfigState accountConfigState,
                        AccountManagerImpl accountManager,
                        StrategyManager strategyManager) {
        this.config = config;
        this.strategyConfigState = strategyConfigState;
        this.infoState = infoState;
        this.assetState = assetState;
        this.accountConfigState = accountConfigState;
        this.accountManager = accountManager;
        this.strategyManager = strategyManager;
    }

    public void start() throws InitializeException {
        // 初始化四个配置state
        accountConfigState = new AccountConfigState(new AccountConfigStore());
        assetState = new AccountAssetState(new AssetBaseValueStore());
        infoState = new InfoState<>(new Info0Store());
        strategyConfigState = new StrategyConfigState(new StrategyConfigStore());

        accountManager = new AccountManagerImpl(accountConfigState, infoState);

        StrategyFactory strategyFactory = new StrategyFactoryImpl(this);
        strategyManager = new AsyncMultiStrategyManager(strategyFactory,
                accountManager.getBlockingQueue(), new HistoryOrderListener(new ExOrderStore()));

        accountManager.start();
        strategyManager.start();
    }

    public void stop() {
        strategyManager.stop();
        accountManager.stop();

        MonitorReporter.stop();
        try {
            DatabaseStore.close();
        } catch (IOException e) {
            logger.warn("failed to close database store normally.");
        }
    }

    public void startStrategies() throws InitializeException {
        // 启动做市策略，按照优先级排序启动，从低到高
        List<StrategyConfig> strategyConfigs = strategyConfigState.get();
        strategyConfigs.sort(Comparator.comparing(StrategyConfig::getPriority));
        for (StrategyConfig strategyConfig: strategyConfigs) {
            try {
                strategyManager.startStrategy(strategyConfig);
            } catch (StrategyException e) {
                throw new InitializeException("failed to start strategy: " + strategyConfig.getName(), e);
            }
        }
    }

    public StrategyConfigState getStrategyConfigState() {
        return strategyConfigState;
    }

    public InfoState<Info0, ?> getInfoState() {
        return infoState;
    }

    public AccountAssetState getAssetState() {
        return assetState;
    }

    public AccountConfigState getAccountConfigState() {
        return accountConfigState;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public StrategyManager getStrategyManager() {
        return strategyManager;
    }

    public List<AccountProfit> getAccountProfit() throws ServiceException {
        // 收集所有的account values
        List<AccountValue> accountValues = new ArrayList<>();
        // 所有账户的资产余额
        for (Exchange ex : accountManager.getAccounts()) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_BALANCES));
            if (msg.getType() == ExMessage.ExMsgType.ERROR) {
                throw new ServiceException("failed to get account values: " + ex.getName());
            }
            List<AccountValue> values = (List<AccountValue>) msg.getData();
            accountValues.addAll(values);
        }

        Map<String, Double> assetPrices = getAssetPrices();

        List<AccountProfit> profits = new ArrayList<>();
        // 找初始值，减之
        for (AccountValue value : accountValues) {
            if (hasBaseValue(value.account, value.asset)) {
                double lastSize = value.totalBalance;
                lastSize = Math.round(lastSize * 100) * 1.0 / 100;
                double size = value.totalBalance -
                        getBaseValue(value.account, value.asset);
                size = Math.round(size * 100) * 1.0 / 100;
                double price = assetPrices.getOrDefault(value.asset, 1.0);
                profits.add(new AccountProfit(value.asset, value.account,
                        lastSize, size, price, Math.round(size * price)));
            }
        }

        return profits;
    }

    public Map<String, Double> getAssetPrices() throws ServiceException {
        return Map.of();
    }

    /**
     * 策略执行通知
     * @param notification 通知
     */
    public void notifyStrategy(Notification notification) {
        accountManager.getBlockingQueue().add(notification);
    }

    // infos查询
    public List<Info0> getInfoByName(String name) {
        return infoState.getInfoByName(name);
    }

    public List<Info0> getInfos() {
        return infoState.getInfos();
    }

    // 获取策略
    public List<StrategyConfig> getStrategyConfigs() {
        return strategyConfigState.get();
    }

    // asset base value
    public boolean hasBaseValue(String account, String asset) {
        return assetState.hasAccountAssetBaseValue(account, asset);
    }

    public double getBaseValue(String account, String asset) {
        return assetState.getAccountAssetBaseValue(account, asset);
    }

    // account
    public Exchange getAccount(String account) {
        return accountManager.getAccount(account);
    }

    public ResourceConfig getResourceConfig() {
        return new AppResource(this);
    }
}
