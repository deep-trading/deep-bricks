package org.eurekaka.bricks.common.model;

public class ExMessage<T> {

    private final ExMsgType type;
    private final T data;

    public ExMessage(ExMsgType type) {
        this.type = type;
        this.data = null;
    }

    public ExMessage(ExMsgType type, T data) {
        this.type = type;
        this.data = data;
    }

    public ExMsgType getType() {
        return type;
    }

    public T getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ExMessage{" +
                "type=" + type +
                ", data=" + data +
                '}';
    }

    public enum ExMsgType {
        // 若当前process内的switch没有找到对应的处理类型，则返回unknown，便于子类继续处理
        // 所以，必须保证该类型不会出现在exchange对象外部
        UNKNOWN,

        // 处理错误
        ERROR,

        // 返回正确结果
        RIGHT,
    }


}
