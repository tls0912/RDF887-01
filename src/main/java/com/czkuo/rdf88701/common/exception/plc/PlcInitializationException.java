package com.czkuo.rdf88701.common.exception.plc;

/**
 * 通訊初始化失敗（握手、版本協商等錯誤）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcInitializationException extends PlcCommunicationException {

    public PlcInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
