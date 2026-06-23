package com.czkuo.rdf88701.common.exception.plc;

/**
 * 不支援的 PLC 通訊協定（如協定未實作或錯誤設定）
 */
public class PlcUnsupportedProtocolException extends PlcCommunicationException {

    public PlcUnsupportedProtocolException(String protocol) {
        super("不支援的通訊協定: " + protocol);
    }
}
