package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 無法建立基礎連線（例如網路錯誤）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcConnectionException extends PlcCommunicationException {

    public PlcConnectionException(String message) {
        super(message);
    }

    public PlcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
