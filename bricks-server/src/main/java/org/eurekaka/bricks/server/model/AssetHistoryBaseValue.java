package org.eurekaka.bricks.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class AssetHistoryBaseValue {
    private int id;
    private String asset;
    private String account;
    @JsonProperty("last_value")
    private double lastValue;
    @JsonProperty("update_value")
    private double updateValue;
    @JsonProperty("current_value")
    private double currentValue;
    private String comment;
    private long timestamp;

    public AssetHistoryBaseValue() {
    }

    public AssetHistoryBaseValue(int id, String asset, String account,
                                 double lastValue, double updateValue,
                                 double currentValue, String comment, long timestamp) {
        this.id = id;
        this.asset = asset;
        this.account = account;
        this.lastValue = lastValue;
        this.updateValue = updateValue;
        this.currentValue = currentValue;
        this.comment = comment;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public double getLastValue() {
        return lastValue;
    }

    public void setLastValue(double lastValue) {
        this.lastValue = lastValue;
    }

    public double getUpdateValue() {
        return updateValue;
    }

    public void setUpdateValue(double updateValue) {
        this.updateValue = updateValue;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AssetHistoryBaseValue)) return false;
        AssetHistoryBaseValue that = (AssetHistoryBaseValue) o;
        return id == that.id &&
                Double.compare(that.lastValue, lastValue) == 0 &&
                Double.compare(that.updateValue, updateValue) == 0 &&
                Double.compare(that.currentValue, currentValue) == 0 &&
                timestamp == that.timestamp &&
                asset.equals(that.asset) &&
                account.equals(that.account) &&
                Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, asset, account, lastValue, updateValue, currentValue, comment, timestamp);
    }

    @Override
    public String toString() {
        return "AccountHistoryBaseValue{" +
                "id=" + id +
                ", asset='" + asset + '\'' +
                ", account='" + account + '\'' +
                ", lastValue=" + lastValue +
                ", updateValue=" + updateValue +
                ", currentValue=" + currentValue +
                ", comment='" + comment + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
