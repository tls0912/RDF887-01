package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * L005 - WIP_Load 請求讀取 1D Barcode 指令 Payload
 * 廠商 → ASE 發送條碼資訊，請求檢查是否允許入 STK
 */
@Data
public class L005CommandPayload {

    /** 指令類型，固定為 LOAD */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 L005 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 訊息流水號（格式 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 BARCODE_CHECK_EVENT */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 條碼資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果狀態，請求時為空字串 */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充訊息 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 條碼值（1D Barcode） */
        @JsonProperty("BARCODE")
        private String barcode;
    }
}
