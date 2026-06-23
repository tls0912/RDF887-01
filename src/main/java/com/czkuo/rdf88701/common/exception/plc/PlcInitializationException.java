package com.czkuo.rdf88701.common.exception.plc;

/**
 * 通訊初始化失敗（握手、版本協商等錯誤）
 */
public class PlcInitializationException extends PlcCommunicationException {

    public PlcInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
