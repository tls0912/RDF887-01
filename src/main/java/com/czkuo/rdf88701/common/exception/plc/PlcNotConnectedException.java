package com.czkuo.rdf88701.common.exception.plc;

/**
 * 尚未建立 PLC 連線
 */
public class PlcNotConnectedException extends PlcCommunicationException {

    public PlcNotConnectedException(String deviceName) {
        super("PLC 裝置未連線: " + deviceName);
    }
}
