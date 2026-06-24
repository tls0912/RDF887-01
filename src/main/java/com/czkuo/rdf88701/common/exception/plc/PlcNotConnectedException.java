package com.czkuo.rdf88701.common.exception.plc;

/**
 * 尚未建立 PLC 連線
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcNotConnectedException extends PlcCommunicationException {

    public PlcNotConnectedException(String deviceName) {
        super("PLC 裝置未連線: " + deviceName);
    }
}
