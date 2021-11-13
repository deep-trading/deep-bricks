package org.eurekaka.bricks.server.model;

import java.util.Objects;

public class WarehouseMetaValue {
    private int id;
    private String tableName;
    private String host;
    private long time;
    private String path;
    private boolean committed;

    public WarehouseMetaValue(int id, String tableName, String host, long time, String path, boolean committed) {
        this.id = id;
        this.tableName = tableName;
        this.host = host;
        this.time = time;
        this.path = path;
        this.committed = committed;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    @Override
    public String toString() {
        return "WarehouseMetaValue{" +
                "id=" + id +
                ", tableName='" + tableName + '\'' +
                ", host='" + host + '\'' +
                ", time=" + time +
                ", path='" + path + '\'' +
                ", committed=" + committed +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WarehouseMetaValue that = (WarehouseMetaValue) o;
        return id == that.id &&
                time == that.time &&
                committed == that.committed &&
                tableName.equals(that.tableName) &&
                host.equals(that.host) &&
                path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tableName, host, time, path, committed);
    }
}
