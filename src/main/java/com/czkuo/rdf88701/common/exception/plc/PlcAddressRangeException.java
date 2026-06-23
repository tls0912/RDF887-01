package com.czkuo.rdf88701.common.exception.plc;

/**
 * PLC 地址區段錯誤（起始位址、長度非法）
 */
public class PlcAddressRangeException extends PlcCommunicationException {

    public PlcAddressRangeException(String message) {
        super(message);
    }
}
