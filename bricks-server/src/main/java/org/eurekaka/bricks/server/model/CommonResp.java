package org.eurekaka.bricks.server.model;

import java.util.Objects;

public class CommonResp<T> {
    private int code;
    private T result;

    public CommonResp() {
    }

    public CommonResp(int code, T result) {
        this.code = code;
        this.result = result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommonResp)) return false;
        CommonResp<?> that = (CommonResp<?>) o;
        return code == that.code &&
                Objects.equals(result, that.result);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, result);
    }

    @Override
    public String toString() {
        return "Resp{" +
                "code=" + code +
                ", result=" + result +
                '}';
    }
}
