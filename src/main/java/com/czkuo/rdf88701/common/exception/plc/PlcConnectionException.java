package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 無法建立基礎連線（例如網路錯誤）
 */
public class PlcConnectionException extends PlcCommunicationException {

    public PlcConnectionException(String message) {
        super(message);
    }

    public PlcConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
