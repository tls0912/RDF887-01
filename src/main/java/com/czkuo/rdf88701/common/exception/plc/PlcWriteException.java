package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 寫入資料異常
 */
public class PlcWriteException extends PlcCommunicationException {

    public PlcWriteException(String message) {
        super(message);
    }

    public PlcWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
