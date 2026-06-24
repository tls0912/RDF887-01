package com.czkuo.rdf88701.common.exception;

/**
 * 自訂業務異常
 * 用於 Application Layer & Domain Layer 的業務錯誤處理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;

    /**
     * 建議：提供 errorCode（可選，保留擴展性）
     */
    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_ERROR";
    }

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
