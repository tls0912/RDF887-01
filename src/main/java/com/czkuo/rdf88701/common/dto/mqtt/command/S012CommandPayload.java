package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S012 指令：人員觸發關閉安全門
 * 廠商主動通知 ASE 有人要求關門，ASE 需進行人員資格驗證
 */
@Data
public class S012CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S012" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，用於請求與回覆對應 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "CLOSE_DOOR_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體內容 */
    @JsonProperty("MESSAGE")
    private S012CommandPayload.Message message;

    /** 執行結果欄位（請求方為空，由回覆方填入） */
    @JsonProperty("RESULT")
    private String result;

    /** 執行結果說明（可為空文字，或補充失敗說明） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：維修門資訊
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 各機構區安全門名稱 */
        @JsonProperty("SAFE_DOOR_NAME")
        private String safeDoorName;
    }
}
