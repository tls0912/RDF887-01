package com.czkuo.rdf88701.common.dto.mqtt.command;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S008 指令：SEEC → SAA 發送 WARNING 警告訊息
 */
@Data
public class S008CommandPayload {

    /** 指令類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S008 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 WARNING */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 警告內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（初始為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（初始為空字串，可為 null） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 發送警報的設備名稱（如 AGV01） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 警告代碼（字串型） */
        @JsonProperty("ALID")
        private String alid;

        /** 警告說明（英文） */
        @JsonProperty("ALID_DESC_EN")
        private String alidDescEn;

        /** 警告說明（中文） */
        @JsonProperty("ALID_DESC_CH")
        private String alidDescCh;

        /** 警報狀態：START or END */
        @JsonProperty("ALARM_CD")
        private String alarmCode;
    }
}
