package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S007 指令：SEEC → SAA 發送警報事件
 */
@Data
public class S007CommandPayload {

    /** 指令類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S007 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 ALARM） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 警報內容 */
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

        /** 警報代碼 ID */
        @JsonProperty("ALID")
        private String alid;

        /** 警報說明（英文） */
        @JsonProperty("ALID_DESC_EN")
        private String alidDescEn;

        /** 警報說明（中文） */
        @JsonProperty("ALID_DESC_CH")
        private String alidDescCh;

        /** 警報狀態：START or END */
        @JsonProperty("ALARM_CD")
        private String alarmCode;
    }
}
