package com.czkuo.rdf88701.common.exception.plc;

/**
 * 不支援的 PLC 通訊協定（如協定未實作或錯誤設定）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcUnsupportedProtocolException extends PlcCommunicationException {

    public PlcUnsupportedProtocolException(String protocol) {
        super("不支援的通訊協定: " + protocol);
    }
}
