package org.eurekaka.bricks.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class QueryData {

    private boolean success;
    private List<Map<String, Object>> data;
    private String msg;

    public QueryData(boolean success) {
        this.success = success;
        this.data = new ArrayList<>();
    }

    public QueryData(boolean success, String msg) {
        this.success = success;
        this.data = new ArrayList<>();
        this.msg = msg;
    }

    public QueryData(boolean success, List<Map<String, Object>> data) {
        this.success = success;
        this.data = data;
    }

    public QueryData() {
        this.success = true;
        this.data = new ArrayList<>();
    }

    public void addElement(Map<String, Object> element) {
        this.data.add(element);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "QueryData{" +
                "success=" + success +
                ", data=" + data +
                ", msg='" + msg + '\'' +
                '}';
    }
}
