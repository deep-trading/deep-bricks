package org.eurekaka.bricks.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 账户资产初始值配置表
 */
public class AssetBaseValue {
    private int id;

    // 资产名称
    private String asset;

    // 账户名称
    private String account;

    // 初始余额
    @JsonProperty("base_value")
    private double baseValue;

    private boolean enabled;

    public AssetBaseValue() {
    }

    public AssetBaseValue(int id, String asset, String account,
                          double baseValue, boolean enabled) {
        this.id = id;
        this.asset = asset;
        this.account = account;
        this.baseValue = baseValue;
        this.enabled = enabled;
    }

    public String getAsset() {
        return asset;
    }

    public void setAsset(String asset) {
        this.asset = asset;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public double getBaseValue() {
        return baseValue;
    }

    public void setBaseValue(double baseValue) {
        this.baseValue = baseValue;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetBaseValue)) return false;
        AssetBaseValue that = (AssetBaseValue) o;
        return id == that.id &&
                Double.compare(that.baseValue, baseValue) == 0 &&
                enabled == that.enabled &&
                asset.equals(that.asset) &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, asset, account, baseValue, enabled);
    }

    @Override
    public String toString() {
        return "AccountBaseValue{" +
                "id=" + id +
                ", asset='" + asset + '\'' +
                ", account='" + account + '\'' +
                ", baseValue=" + baseValue +
                ", enabled=" + enabled +
                '}';
    }
}
