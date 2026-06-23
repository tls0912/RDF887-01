package com.czkuo.rdf88701.common.exception;

/**
 * 表示握手流程中發生異常（例如 PLC 未就緒、業務條件未滿足等）
 * 可根據例外種類決定是否重試或直接失敗。
 */
public class HandshakeException extends RuntimeException {

    public enum Type {
        RETRY,   // 可重試
        FAIL     // 不可重試，直接失敗
    }

    private final Type type;

    private HandshakeException(String message, Type type) {
        super(message);
        this.type = type;
    }

    public static HandshakeException retry(String message) {
        return new HandshakeException(message, Type.RETRY);
    }

    public static HandshakeException fail(String message) {
        return new HandshakeException(message, Type.FAIL);
    }

    public boolean isRetryable() {
        return this.type == Type.RETRY;
    }

    public boolean isFatal() {
        return this.type == Type.FAIL;
    }

    public Type getType() {
        return type;
    }
}
