package org.eurekaka.bricks.server.service;

import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.manager.AccountAssetState;
import org.eurekaka.bricks.server.manager.AccountConfigState;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;

import java.util.List;

public class AccountAssetService {

    protected final InfoState<Info0, ?> infoState;
    protected final AccountAssetState assetState;

    protected final AccountConfigState accountConfigState;

    public AccountAssetService(BrickContext brickContext) {
        this.infoState = brickContext.getInfoState();
        this.assetState = brickContext.getAssetState();
        this.accountConfigState = brickContext.getAccountConfigState();
    }

    public List<AssetBaseValue> query() throws ServiceException {
        try {
            return assetState.queryAllAccountBaseValues();
        } catch (StoreException e) {
            throw new ServiceException("failed to query asset values", e);
        }
    }

    public List<AssetHistoryBaseValue> queryHistoryAsset(
            long start, long stop, int limit) throws ServiceException {
        try {
            return assetState.queryAssetHistory(start, stop, limit);
        } catch (StoreException e) {
            throw new ServiceException("failed to query history asset", e);
        }
    }

    public AssetBaseValue store(AssetBaseValue value) throws ServiceException {
        try {
            assetState.addAccountBaseValue(value);
            return value;
        } catch (StoreException e) {
            throw new ServiceException("failed to store asset value: " + value, e);
        }
    }

    public void update(AssetHistoryBaseValue value) throws ServiceException {
        try {
            assetState.updateAccountBaseValue(value);
        } catch (StoreException e) {
            throw new ServiceException("failed to update asset value: " + value, e);
        }
    }

    public void delete(int id) throws ServiceException {
        try {
            assetState.deleteAccountBaseValue(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to delete asset value: " + id, e);
        }
    }

    public void enable(int id) throws ServiceException {
        AssetBaseValue value;
        try {
            value = assetState.queryAccountBaseValue(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to find asset: " + id, e);
        }
        if (value != null && !value.isEnabled()) {
            // 检查account是否已经enabled
            boolean enabled = false;
            for (AccountConfig accountConfig : accountConfigState.getAccountConfigs()) {
                if (accountConfig.getName().equals(value.getAccount())) {
                    enabled = true;
                    break;
                }
            }
            if (!enabled) {
                throw new ServiceException("failed to enable asset value, account not enabled");
            }
            try {
                assetState.enableAccountBaseValue(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to enable asset value", e);
            }
        }
    }

    /**
     * 保证没有info使用该asset，一般来说，检测info prooerty中的asset字段
     * 若需要更强检测，需要override该方法
     * @param id asset id
     * @throws ServiceException 执行失败
     */
    public void disable(int id) throws ServiceException {
        AssetBaseValue value;
        try {
            value = assetState.queryAccountBaseValue(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to find asset value: " + id, e);
        }
        if (value != null && value.isEnabled()) {
            for (Info0 info : infoState.getInfos()) {
                if (value.getAsset().equals(info.getProperty("asset")) &&
                        value.getAccount().equals(info.getAccount())) {
                    throw new ServiceException("info " + info.getName() + " is using the asset, disable failed");
                }
            }
            try {
                assetState.disableAccountBaseValue(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to disable asset", e);
            }
        }
    }

}
