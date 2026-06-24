package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 資料讀取異常
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcReadException extends PlcCommunicationException {

    public PlcReadException(String message) {
        super(message);
    }

    public PlcReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
