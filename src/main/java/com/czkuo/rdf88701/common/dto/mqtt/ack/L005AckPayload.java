package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * L005 - WIP_Load 條碼回覆 Payload
 * ASE → 廠商 回傳條碼解析後的資訊與入 STK 判定結果
 */
@Data
public class L005AckPayload {

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

    /** 條碼相關資訊與判定 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回應結果：PASS（可入 STK） / FAIL（不可入 STK） */
    @JsonProperty("RESULT")
    private String result;

    /** 失敗原因或補充訊息 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 條碼值（1D Barcode） */
        @JsonProperty("BARCODE")
        private String barcode;

        /** 盒號識別碼（如 TY0001VM） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 批號（LOT ID） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** Tray 厚度資訊 */
        @JsonProperty("TRAY_HIGH")
        private String trayHigh;

        /** Tray 類型 */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** 訊息分類（錯誤訊息類別） */
        @JsonProperty("MESSAGE_TYPE")
        private String messageType;
    }
}
