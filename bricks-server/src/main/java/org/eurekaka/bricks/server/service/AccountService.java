package org.eurekaka.bricks.server.service;

import org.eurekaka.bricks.api.AccountManager;
import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.manager.AccountAssetState;
import org.eurekaka.bricks.server.manager.AccountConfigState;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;
import org.eurekaka.bricks.server.store.BalanceStore;
import org.eurekaka.bricks.server.store.FutureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 对账，查询快照，调整账户杠杆，转入转出资产
 */
public class AccountService {
    private final static Logger logger = LoggerFactory.getLogger(AccountService.class);

    protected final AccountManager accountManager;
    protected final AccountAssetState assetState;
    protected final AccountConfigState accountConfigState;
    protected final InfoState<Info0, ?> infoState;

    private final FutureStore futureStore;
    private final BalanceStore balanceStore;

    public AccountService(BrickContext brickContext) {
        this(brickContext, new FutureStore(), new BalanceStore());
    }

    public AccountService(BrickContext brickContext,
                          FutureStore futureStore,
                          BalanceStore balanceStore) {
        this.accountManager = brickContext.getAccountManager();
        this.accountConfigState = brickContext.getAccountConfigState();
        this.assetState = brickContext.getAssetState();
        this.infoState = brickContext.getInfoState();

        this.futureStore = futureStore;
        this.balanceStore = balanceStore;
    }

    public List<AccountProfit> getAccountProfit() throws ServiceException {
        List<AccountValue> accountValues = getAccountValues();

        List<AccountProfit> profits = new ArrayList<>();
        // 找初始值，减之
        for (AccountValue value : accountValues) {
            if (assetState.hasAccountAssetBaseValue(value.account, value.asset)) {
                double lastSize = value.totalBalance;
                lastSize = Math.round(lastSize * 100) * 1.0 / 100;
                double size = value.totalBalance -
                        assetState.getAccountAssetBaseValue(value.account, value.asset);
                size = Math.round(size * 100) * 1.0 / 100;

//                double price = assetPrices.getOrDefault(value.asset, 1.0);
                double price = 1.0;

                profits.add(new AccountProfit(value.asset, value.account,
                        lastSize, size, price, Math.round(size * price)));
            }
        }

        return profits;
    }


    public List<AccountValue> getAccountValues() {
        // 收集所有的account values
        List<AccountValue> accountValues = new ArrayList<>();
        // 所有账户的资产余额
        for (Exchange ex : accountManager.getAccounts()) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_BALANCES));
            if (msg.getType() == ExMessage.ExMsgType.RIGHT) {
                List<AccountValue> values = (List<AccountValue>) msg.getData();
                accountValues.addAll(values);
            } else {
                logger.error("failed to get account values for {}", ex.getName(), (Exception) msg.getData());
            }
        }
        return accountValues;
    }


    public List<PositionValue> getPositionValues() {
        List<PositionValue> positionValues = new ArrayList<>();
        for (Exchange ex : accountManager.getAccounts()) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_POSITIONS));
            if (msg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                positionValues.addAll((List<PositionValue>) msg.getData());
            } else {
                logger.error("failed to get positions for {}", ex.getName(), (Exception) msg.getData());
            }
        }
        return positionValues;
    }

    public List<PositionValue> queryPositionValues(long time) throws ServiceException {
        try {
            return futureStore.queryPositionValueByTime(roundTime(time));
        } catch (StoreException e) {
            throw new ServiceException("failed to query position at time: " + time, e);
        }
    }

    public List<FundingValue> queryFundingValues(long time) throws ServiceException {
        if (time == 0) {
            time = System.currentTimeMillis();
        }
        try {
            return futureStore.queryFundingValueByTime(time / 3600000 * 3600000);
        } catch (StoreException e) {
            throw new ServiceException("failed to query funding value by time: " + time, e);
        }
    }

    public List<AccountProfit> queryAccountBalances(long time) throws ServiceException {
        try {
            return balanceStore.queryAccountProfit(roundTime(time));
        } catch (StoreException e) {
            throw new ServiceException("failed to query account profit by time: " + time, e);
        }
    }

    private long roundTime(long time) {
        if (time == 0) {
            time = System.currentTimeMillis();
        }
        return time / 60000 * 60000;
    }


    public void transfer(String fromAccount, String toAccount,
                         String asset, double amount) throws ServiceException {
        AccountConfig fromAccountConfig = null;
        AccountConfig toAccountConfig = null;
        for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
            if (accountConfig.getName().equals(fromAccount)) {
                fromAccountConfig = accountConfig;
            }
            if (accountConfig.getName().equals(toAccount)) {
                toAccountConfig = accountConfig;
            }
        }
        if (fromAccountConfig == null || toAccountConfig == null) {
            throw new ServiceException("failed to find account config, from " + fromAccount + " to " + toAccount);
        }

        AssetTransfer transfer = new AssetTransfer(fromAccountConfig, toAccountConfig, asset, amount);

        transfer(transfer);


        updateAccounts(fromAccount, toAccount, asset, amount);
    }

    public void transfer(String account, int type,
                         String asset, double amount) throws ServiceException {
        AccountConfig fromAccountConfig = null;
        for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
            if (accountConfig.getName().equals(account)) {
                fromAccountConfig = accountConfig;
            }
        }
        if (fromAccountConfig == null) {
            throw new ServiceException("failed to find account config: " + account);
        }
        if (type == 0) {
            throw new ServiceException("transfer type can not be 0");
        }

        AssetTransfer transfer = new AssetTransfer(fromAccountConfig, type, asset, amount);
        transfer(transfer);
    }

    public void withdraw(String fromAccount, String toAccount,
                         String asset, double amount) throws ServiceException {
        AccountConfig fromAccountConfig = null;
        AccountConfig toAccountConfig = null;
        for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
            if (accountConfig.getName().equals(fromAccount)) {
                fromAccountConfig = accountConfig;
            }
            if (accountConfig.getName().equals(toAccount)) {
                toAccountConfig = accountConfig;
            }
        }
        if (fromAccountConfig == null || toAccountConfig == null) {
            throw new ServiceException("failed to find account config, from " + fromAccount + " to " + toAccount);
        }

        AssetTransfer transfer = new AssetTransfer(fromAccountConfig, toAccountConfig, asset, amount);

        withdraw(transfer);

        updateAccounts(fromAccount, toAccount, asset, amount);
    }

    public List<AccountAssetRecord> getAssetRecords(
            String account, int type, long start, long stop, int limit) throws ServiceException {
        AccountConfig fromAccountConfig = null;
        for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
            if (accountConfig.getName().equals(account)) {
                fromAccountConfig = accountConfig;
            }
        }
        if (fromAccountConfig == null) {
            throw new ServiceException("failed to find account config: " + account);
        }

        AssetTransferHistory transferHistory = new AssetTransferHistory(account, type, start, stop, limit);

        return getTransferRecords(transferHistory);
    }


    private void updateAccounts(String fromAccount, String toAccount,
                                String asset, double amount) throws ServiceException {
        // 找到对应的base value
        double fromAccountBaseValue = 0;
        boolean foundFrom = false;
        double toAccountBaseValue = 0;
        boolean foundTo = false;
        for (AssetBaseValue baseValue : assetState.getAccountAssetBaseValues()) {
            if (baseValue.getAccount().equals(fromAccount) && baseValue.getAsset().equals(asset)) {
                fromAccountBaseValue = baseValue.getBaseValue();
                foundFrom = true;
            }
            if (baseValue.getAccount().equals(toAccount) && baseValue.getAsset().equals(asset)) {
                toAccountBaseValue = baseValue.getBaseValue();
                foundTo = true;
            }
        }
        if (!foundFrom || !foundTo) {
            // 有部分配置的资产基础值没有找到
            throw new ServiceException("failed to find from base value or to base value");
        }

        // 若正常完成，则更新对应的base value值
        try {
            String fromComment = "sent " + amount + " " + asset + " to " + toAccount;
            String toComment = "received " + amount + " " + asset + " from " + fromAccount;
            long currentTime = System.currentTimeMillis();
            assetState.updateAccountBaseValue(new AssetHistoryBaseValue(0, asset, fromAccount,
                    fromAccountBaseValue, -amount, fromAccountBaseValue - amount,
                    fromComment, currentTime));
            assetState.updateAccountBaseValue(new AssetHistoryBaseValue(0, asset, toAccount,
                    toAccountBaseValue, amount, toAccountBaseValue + amount,
                    toComment, currentTime));
        } catch (StoreException e) {
            throw new ServiceException("failed to update asset base value", e);
        }
    }


    private void transfer(AssetTransfer transfer) throws ServiceException {
        Exchange ex = accountManager.getAccount(transfer.fromAccountConfig.getName());
        if (ex != null) {
            ExMessage<?> transferMsg = ex.process(new ExAction<>(
                    ExAction.ActionType.TRANSFER_ASSET, transfer));
            if (transferMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                logger.error("failed to transfer for: " + ex.getName(),
                        (Exception) transferMsg.getData());
                throw new ServiceException("failed to transfer asset: " + transfer.asset);
            }
        }
    }

    private void withdraw(AssetTransfer transfer) throws ServiceException {
        Exchange ex = accountManager.getAccount(transfer.fromAccountConfig.getName());
        if (ex != null) {
            ExMessage<?> transferMsg = ex.process(new ExAction<>(
                    ExAction.ActionType.WITHDRAW_ASSET, transfer));
            if (transferMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                logger.error("failed to withdraw for: " + ex.getName(),
                        (Exception) transferMsg.getData());
                throw new ServiceException("failed to withdraw asset: " + transfer.asset);
            }
        }
    }

    private List<AccountAssetRecord> getTransferRecords(AssetTransferHistory transferHistory) throws ServiceException {
        Exchange ex = accountManager.getAccount(transferHistory.account);
        if (ex != null) {
            ExMessage<?> transferMsg = ex.process(new ExAction<>(
                    ExAction.ActionType.GET_ASSET_RECORDS, transferHistory));
            if (transferMsg.getType().equals(ExMessage.ExMsgType.ERROR)) {
                logger.error("failed to get transfer records: " + transferHistory,
                        (Exception) transferMsg.getData());
                throw new ServiceException("failed to get transfer records: " + transferHistory);
            }
            return (List<AccountAssetRecord>) transferMsg.getData();
        }
        return null;
    }


    public RiskLimitValue getRiskLimitValue(String account) {
        Exchange ex = accountManager.getAccount(account);
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_RISK_LIMIT));
            if (msg.getType() == ExMessage.ExMsgType.RIGHT) {
                return (RiskLimitValue) msg.getData();
            }
        }
        return null;
    }

    public boolean updateRiskLimit(String account, String futureName, int leverage) {
        RiskLimitPair riskLimitPair = null;
        for (Info<?> info : infoState.getInfos()) {
            if (info.getAccount().equals(account) && info.getName().equals(futureName)) {
                riskLimitPair = new RiskLimitPair(futureName, info.getSymbol(), leverage);
            }
        }

        Exchange ex = accountManager.getAccount(account);
        if (ex != null) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.UPDATE_RISK_LIMIT, riskLimitPair));
            return msg.getType() == ExMessage.ExMsgType.RIGHT;
        }
        return false;
    }

    /**
     * 查询所有账户的资金费用
     * 只查询过去一个小时最近一次的资金费用，若存在则返回
     * @return 所有账户下交易对的资金费率
     */
    public List<FundingValue> getFundingValues() {
        List<FundingValue> fundingValues = new ArrayList<>();
        for (Exchange ex : accountManager.getAccounts()) {
            ExMessage<?> msg = ex.process(new ExAction<>(ExAction.ActionType.GET_FUNDING_FEES,
                    System.currentTimeMillis() - 3600000));
            if (msg.getType().equals(ExMessage.ExMsgType.RIGHT)) {
                fundingValues.addAll((List<FundingValue>) msg.getData());
            } else {
                logger.error("failed to get funding values for {}", ex.getName(), (Exception) msg.getData());
            }
        }
        return fundingValues;
    }

}
