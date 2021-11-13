package org.eurekaka.bricks.server.manager;

import org.eurekaka.bricks.common.exception.InitializeException;
import org.eurekaka.bricks.common.exception.StoreException;
import org.eurekaka.bricks.common.model.AssetBaseValue;
import org.eurekaka.bricks.server.model.AssetHistoryBaseValue;
import org.eurekaka.bricks.server.store.AssetBaseValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 初始资产管理器
 * 所有资产的初始状态变动，均由此操作
 * 例如充值提现操作，会使得资产初始值变动
 * 资产价值计算方式由账户内当前资产，转换为USDT（USDC）价值统一计价后，整体相加
 */
public class AccountAssetState {
    private final static Logger logger = LoggerFactory.getLogger(AccountAssetState.class);

    // account -> asset -> base value
    private final Map<String, Map<String, AssetBaseValue>> baseValues;
    private final AssetBaseValueStore assetBaseValueStore;

    public AccountAssetState(AssetBaseValueStore assetBaseValueStore) {
        baseValues = new ConcurrentHashMap<>();
        this.assetBaseValueStore = assetBaseValueStore;

        try {
            for (AssetBaseValue value : assetBaseValueStore.queryAssetBaseValues()) {
                if (value.isEnabled()) {
                    if (!baseValues.containsKey(value.getAccount())) {
                        baseValues.put(value.getAccount(), new ConcurrentHashMap<>());
                    }
                    baseValues.get(value.getAccount()).put(value.getAsset(), value);
                    logger.info("initial base value: {}", value);
                }
            }
        } catch (StoreException e) {
            throw new InitializeException("failed to initialize account asset state.", e);
        }
    }

    public boolean hasAccountAssetBaseValue(String account, String asset) {
        return baseValues.containsKey(account) && baseValues.get(account).containsKey(asset);
    }

    // todo:: 注意必须保证有对应的account 与 asset
    public double getAccountAssetBaseValue(String account, String asset) {
        return baseValues.get(account).get(asset).getBaseValue();
    }

    public List<AssetBaseValue> getAccountAssetBaseValues() {
        List<AssetBaseValue> values = new ArrayList<>();
        for (Map<String, AssetBaseValue> entry : baseValues.values()) {
            values.addAll(entry.values());
        }
        return values;
    }

    public List<AssetBaseValue> queryAllAccountBaseValues() throws StoreException {
        return assetBaseValueStore.queryAssetBaseValues();
    }

    public AssetBaseValue queryAccountBaseValue(int id) throws StoreException {
        return assetBaseValueStore.queryAssetBaseValue(id);
    }

    /**
     * 删除base value
     * 必须保证没有其他表内，引用到该资产，所以必须使用foreign key配置sql表
     * @param id 待删除的base value id
     * @throws StoreException 执行失败
     */
    public void deleteAccountBaseValue(int id) throws StoreException {
        AssetBaseValue value = assetBaseValueStore.queryAssetBaseValue(id);
        if (value != null && value.isEnabled()) {
            throw new StoreException("can not delete enabled account base value");
        }

        assetBaseValueStore.deleteAssetBaseValue(id);
    }

    public void addAccountBaseValue(AssetBaseValue value) throws StoreException {
        if (baseValues.containsKey(value.getAccount()) &&
                baseValues.get(value.getAccount()).containsKey(value.getAsset())) {
            // 资产已经存在同一个账户，同一个资产名称下
            throw new StoreException("asset already existed in the account: " + value);
        }

        assetBaseValueStore.storeAssetBaseValue(value);
        logger.info("added new asset base value: {}", value);

//        if (!baseValues.containsKey(value.getAccount())) {
//            baseValues.put(value.getAccount(), new ConcurrentHashMap<>());
//        }
//        baseValues.get(value.getAccount()).put(value.getAsset(), value);
    }

    public void updateAccountBaseValue(AssetHistoryBaseValue value) throws StoreException {
//        assetBaseValueStore.updateAssetBaseValue(value);
        assetBaseValueStore.storeAssetHistoryBaseValue(value);
        // 更新内存值
        if (hasAccountAssetBaseValue(value.getAccount(), value.getAsset())) {
            baseValues.get(value.getAccount()).get(value.getAsset()).setBaseValue(value.getCurrentValue());
            logger.info("updated base value: {}", baseValues.get(value.getAccount()).get(value.getAsset()));
        }
    }

    /**
     * 必须在外部先检测该base value没有其他对象引用或者使用时，才能disable
     * @param id 需要disable的account base value id
     * @throws StoreException 操作失败
     */
    public void disableAccountBaseValue(int id) throws StoreException {
        AssetBaseValue value = assetBaseValueStore.queryAssetBaseValue(id);
        if (value != null && value.isEnabled()) {
            value.setEnabled(false);
            assetBaseValueStore.updateAssetBaseValueEnabled(id, false);
            baseValues.get(value.getAccount()).remove(value.getAsset());
            logger.info("disabled asset base value: {}", value);
        }
    }

    public void enableAccountBaseValue(int id) throws StoreException {
        AssetBaseValue value = assetBaseValueStore.queryAssetBaseValue(id);
        if (value != null && !value.isEnabled()) {
            assetBaseValueStore.updateAssetBaseValueEnabled(id, true);
            value.setEnabled(true);
            if (!baseValues.containsKey(value.getAccount())) {
                baseValues.put(value.getAccount(), new ConcurrentHashMap<>());
            }
            baseValues.get(value.getAccount()).put(value.getAsset(), value);
            logger.info("enabled asset base value: {}", value);
        }
    }

    public List<AssetHistoryBaseValue> queryAssetHistory(long start, long stop, int limit) throws StoreException {
        return assetBaseValueStore.queryAssetHistoryBaseValue(start, stop, limit);
    }

}
