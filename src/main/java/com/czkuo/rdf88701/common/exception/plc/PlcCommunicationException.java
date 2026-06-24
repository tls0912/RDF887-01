package com.czkuo.rdf88701.common.exception.plc;

/**
 * 所有 PLC 通訊相關錯誤的基底類別
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcCommunicationException extends RuntimeException {

    public PlcCommunicationException(String message) {
        super(message);
    }

    public PlcCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
