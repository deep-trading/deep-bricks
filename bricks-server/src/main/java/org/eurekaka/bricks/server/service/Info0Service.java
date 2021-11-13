package org.eurekaka.bricks.server.service;


import org.eurekaka.bricks.api.Exchange;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.common.model.Info0;
import org.eurekaka.bricks.common.model.InfoState;
import org.eurekaka.bricks.common.model.StrategyConfig;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.manager.AccountAssetState;
import org.eurekaka.bricks.server.manager.AccountManagerImpl;
import org.eurekaka.bricks.server.state.StrategyConfigState;

import java.util.List;

/**
 * 在rest接口处，根据info0 type字段自定义数据模型，最后统一使用info0存储
 * 不建议直接使用Info0Service
 */
public class Info0Service {

    protected final InfoState<Info0, ?> infoState;
    protected final AccountManagerImpl accountManager;
    protected final AccountAssetState assetState;
    protected final StrategyConfigState configState;

    public Info0Service(BrickContext brickContext) {
        this.infoState = brickContext.getInfoState();
        this.configState = brickContext.getStrategyConfigState();
        this.accountManager = (AccountManagerImpl) brickContext.getAccountManager();
        this.assetState = brickContext.getAssetState();
    }

    public List<Info0> query() throws ServiceException {
        try {
            return infoState.queryAllInfos();
        } catch (StoreException e) {
            throw new ServiceException("failed to query infos", e);
        }
    }

    public Info0 store(Info0 info) throws ServiceException {
        checkStore(info);
        try {
            infoState.updateInfo(info);
            return info;
        } catch (StoreException e) {
            throw new ServiceException("failed to store info: " + info, e);
        }
    }

    public void update(Info0 info) throws ServiceException {
        checkUpdate(info);
        try {
            infoState.updateInfo(info);
        } catch (StoreException e) {
            throw new ServiceException("failed to update info: " + info, e);
        }
    }

    public void delete(int id) throws ServiceException {
        try {
            infoState.deleteInfo(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to delete info: " + id, e);
        }
    }

    public void enable(int id) throws ServiceException {
        Info0 info;
        try {
            info = infoState.queryInfo(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to enable info, not found: " + id, e);
        }
        if (info != null && !info.isEnabled()) {
            // 检查是否有asset与account
            boolean has = false;
            if (info.getProperty(Info0.ASSET_KEY) != null) {
                for (AssetBaseValue value : assetState.getAccountAssetBaseValues()) {
                    if (info.getProperty(Info0.ASSET_KEY).equals(value.getAsset()) &&
                            info.getAccount().equals(value.getAccount())) {
                        has = true;
                    }
                }
                if (!has) {
                    throw new ServiceException("info asset not enabled");
                }
            }
            has = false;
            for (Exchange account : accountManager.getAccounts()) {
                if (account.getName().equals(info.getAccount())) {
                    has = true;
                    break;
                }
            }
            if (!has) {
                throw new ServiceException("info account not enabled");
            }
            processEnable(info);
            accountManager.addSymbol(info);
            try {
                infoState.enableInfo(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to enable info", e);
            }
        }
    }

    public void disable(int id) throws ServiceException {
        Info0 info = infoState.getInfo(id);
        if (info != null) {
            // 检测没有strategy还在使用该info
            int count = 0;
            for (Info0 futureInfo : infoState.getInfos()) {
                if (futureInfo.getName().equals(info.getName())) {
                    count++;
                }
            }
            if (count <= 1) {
                for (StrategyConfig config : configState.get()) {
                    if (config.getInfoName().equals(info.getName())) {
                        throw new ServiceException("failed to disable info, strategy " +
                                config.getName() + " is still using info: " + info.getName());
                    }
                }
            }

            processDisable(info);
            try {
                infoState.disableInfo(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to disable info", e);
            }
            accountManager.removeSymbol(info);
        }
    }

    /**
     * enbale 的自定义检查
     * @param info 目标交易对
     * @throws ServiceException 执行失败
     */
    protected void processEnable(Info0 info) throws ServiceException {}

    protected void processDisable(Info0 info) throws ServiceException {}

    protected void checkStore(Info0 info) throws ServiceException {}

    protected void checkUpdate(Info0 info) throws ServiceException {}
}
