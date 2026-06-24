package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 傳輸資料格式錯誤或轉換異常
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcDataFormatException extends PlcCommunicationException {

    public PlcDataFormatException(String message) {
        super(message);
    }

    public PlcDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
