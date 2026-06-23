package com.czkuo.rdf88701.common.exception.plc;

/**
 * 所有 PLC 通訊相關錯誤的基底類別
 */
public class PlcCommunicationException extends RuntimeException {

    public PlcCommunicationException(String message) {
        super(message);
    }

    public PlcCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
