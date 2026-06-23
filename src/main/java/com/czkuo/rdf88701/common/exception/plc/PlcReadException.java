package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 資料讀取異常
 */
public class PlcReadException extends PlcCommunicationException {

    public PlcReadException(String message) {
        super(message);
    }

    public PlcReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
