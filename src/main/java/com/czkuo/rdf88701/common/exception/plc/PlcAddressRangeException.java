package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 地址區段錯誤（起始位址、長度非法）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public class PlcAddressRangeException extends PlcCommunicationException {

    public PlcAddressRangeException(String message) {
        super(message);
    }
}
