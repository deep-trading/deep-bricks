package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class AssetTransfer {
    public final AccountConfig fromAccountConfig;
    public final AccountConfig toAccountConfig;
    public final int type;
    public final String asset;
    public final double amount;

    public AssetTransfer(AccountConfig fromAccountConfig,
                         int type, String asset, double amount) {
        this.fromAccountConfig = fromAccountConfig;
        this.toAccountConfig = null;
        this.type = type;
        this.asset = asset;
        this.amount = amount;
    }

    public AssetTransfer(AccountConfig fromAccountConfig,
                         AccountConfig toAccountConfig,
                         String asset, double amount) {
        this.fromAccountConfig = fromAccountConfig;
        this.toAccountConfig = toAccountConfig;
        this.type = 0;
        this.asset = asset;
        this.amount = amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetTransfer)) return false;
        AssetTransfer transfer = (AssetTransfer) o;
        return type == transfer.type && Double.compare(transfer.amount, amount) == 0 &&
                fromAccountConfig.equals(transfer.fromAccountConfig) &&
                Objects.equals(toAccountConfig, transfer.toAccountConfig) &&
                asset.equals(transfer.asset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromAccountConfig, toAccountConfig, type, asset, amount);
    }

    @Override
    public String toString() {
        return "AssetTransfer{" +
                "fromAccountConfig=" + fromAccountConfig +
                ", toAccountConfig=" + toAccountConfig +
                ", type=" + type +
                ", asset='" + asset + '\'' +
                ", amount=" + amount +
                '}';
    }
}
