package org.eurekaka.bricks.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QueryRequest {
    // range query
    @JsonProperty("start_time")
    private long startTime;
    @JsonProperty("stop_time")
    private long stopTime;
    @JsonProperty("signed_url")
    private String signedUrl;

    private long interval;
    private String table;
    private String host;

    // some time point data query
    private long time;

    @JsonProperty("query_type")
    // timeseries, timegroup, group
    private String queryType;
    // filter columns
    private List<String> select;

    private List<String> filter;

    private List<String> aggregation;
    private List<String> having;

    private List<String> order;
    private boolean desc;
    private int limit;

    public QueryRequest() {
    }

    public QueryRequest(long startTime, long stopTime, String table) {
        this.startTime = startTime;
        this.stopTime = stopTime;
        this.table = table;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getStopTime() {
        return stopTime;
    }

    public void setStopTime(long stopTime) {
        this.stopTime = stopTime;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public Set<String> getSelectSet() {
        return select == null ? null : new HashSet<>(select);
    }

    public List<String> getSelect() {
        return select;
    }

    public void setSelect(List<String> select) {
        this.select = select;
    }

    public String getSignedUrl() {
        return signedUrl;
    }

    public void setSignedUrl(String signedUrl) {
        this.signedUrl = signedUrl;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public long getInterval() {
        return interval;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public List<String> getFilter() {
        return filter;
    }

    public void setFilter(List<String> filter) {
        this.filter = filter;
    }

    public List<String> getAggregation() {
        return aggregation;
    }

    public void setAggregation(List<String> aggregation) {
        this.aggregation = aggregation;
    }

    public List<String> getHaving() {
        return having;
    }

    public void setHaving(List<String> having) {
        this.having = having;
    }

    public List<String> getOrder() {
        return order;
    }

    public void setOrder(List<String> order) {
        this.order = order;
    }

    public boolean isDesc() {
        return desc;
    }

    public void setDesc(boolean desc) {
        this.desc = desc;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
