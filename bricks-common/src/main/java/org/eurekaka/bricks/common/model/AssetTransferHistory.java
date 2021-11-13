package org.eurekaka.bricks.common.model;

import java.util.Objects;

public class AssetTransferHistory {

    public final String account;
    public final int type;
    public final long start;
    public final long stop;
    public final int limit;

    public AssetTransferHistory(String account, int type, long start, long stop, int limit) {
        this.account = account;
        this.type = type;
        this.start = start;
        this.stop = stop;
        this.limit = limit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetTransferHistory)) return false;
        AssetTransferHistory that = (AssetTransferHistory) o;
        return type == that.type && start == that.start &&
                stop == that.stop && limit == that.limit &&
                account.equals(that.account);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, type, start, stop, limit);
    }

    @Override
    public String toString() {
        return "AssetTransferHistory{" +
                "account='" + account + '\'' +
                ", type=" + type +
                ", start=" + start +
                ", stop=" + stop +
                ", limit=" + limit +
                '}';
    }
}
