package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 傳輸資料格式錯誤或轉換異常
 */
public class PlcDataFormatException extends PlcCommunicationException {

    public PlcDataFormatException(String message) {
        super(message);
    }

    public PlcDataFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
