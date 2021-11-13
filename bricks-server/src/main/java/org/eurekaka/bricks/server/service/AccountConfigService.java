package org.eurekaka.bricks.server.service;

import org.eurekaka.bricks.common.exception.ExchangeException;
import org.eurekaka.bricks.common.exception.ServiceException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.*;
import org.eurekaka.bricks.common.util.Utils;
import org.eurekaka.bricks.server.BrickContext;
import org.eurekaka.bricks.server.manager.AccountAssetState;
import org.eurekaka.bricks.server.manager.AccountConfigState;
import org.eurekaka.bricks.server.manager.AccountManagerImpl;

import java.util.List;

public class AccountConfigService {

    protected final InfoState<Info0, ?> infoState;
    protected final AccountAssetState assetState;

    protected final AccountConfigState accountConfigState;
    protected final AccountManagerImpl accountManager;

    public AccountConfigService(BrickContext brickContext) {
        this.infoState = brickContext.getInfoState();
        this.assetState = brickContext.getAssetState();
        this.accountConfigState = brickContext.getAccountConfigState();
        this.accountManager = (AccountManagerImpl) brickContext.getAccountManager();
    }

    // add, update, delete, query, enable/disable

    public List<AccountConfig> query() throws ServiceException {
        try {
            return accountConfigState.queryAll();
        } catch (StoreException e) {
            throw new ServiceException("failed to query account configs", e);
        }
    }

    public void store(AccountConfig accountConfig) throws ServiceException {
        try {
            accountConfigState.updateAccountConfig(accountConfig);
            accountConfig.setAuthKey(Utils.maskKeySecret(accountConfig.getAuthKey()));
            accountConfig.setAuthSecret(Utils.maskKeySecret(accountConfig.getAuthSecret()));
        } catch (StoreException e) {
            throw new ServiceException("failed to store account config", e);
        }
    }

    public void update(AccountConfig accountConfig) throws ServiceException {
        if (accountConfig.getId() == 0) {
            throw new ServiceException("update unknown account config id: " + accountConfig.getId());
        }
        try {
            accountConfigState.updateAccountConfig(accountConfig);
        } catch (StoreException e) {
            throw new ServiceException("failed to update account config", e);
        }
    }

    public void delete(int id) throws ServiceException {
        try {
            accountConfigState.deleteAccountConfig(id);
        } catch (StoreException e) {
            throw new ServiceException("failed to delete account config id: " + id, e);
        }
    }

    public void enable(int id) throws ServiceException {
        AccountConfig accountConfig = null;
        try {
            accountConfig = accountConfigState.queryAccountConfig(id);
        } catch (StoreException e) {
            throw new ServiceException("can not query account: " + id, e);
        }
        if (accountConfig != null && !accountConfig.isEnabled()) {
            try {
                accountConfigState.enableAccountConfig(id);
                try {
                    accountManager.addAccount(accountConfigState.getAccountConfig(id));
                } catch (ExchangeException e) {
                    accountConfigState.disableAccountConfig(id);
                    throw new ServiceException("failed to add account id: " + id, e);
                }
            } catch (StoreException e) {
                throw new ServiceException("failed to enable account config: " + id, e);
            }
        }
    }

    public void disable(int id) throws ServiceException {
        AccountConfig accountConfig = accountConfigState.getAccountConfig(id);
        if (accountConfig != null) {
            for (Info<Info0> info : infoState.getInfos()) {
                if (info.getAccount().equals(accountConfig.getName())) {
                    throw new ServiceException("info " + info.getName() + " is using account, can not disable");
                }
            }
            for (AssetBaseValue value : assetState.getAccountAssetBaseValues()) {
                if (value.getAccount().equals(accountConfig.getName())) {
                    throw new ServiceException("asset " + value.getAsset() + " is using account, can not disable");
                }
            }
            processDisable(accountConfig);
            try {
                accountConfigState.disableAccountConfig(id);
            } catch (StoreException e) {
                throw new ServiceException("failed to disable account: " + id, e);
            }
            accountManager.removeAccount(accountConfig.getName());
        }
    }

    protected void processDisable(AccountConfig accountConfig) throws ServiceException {}

}
