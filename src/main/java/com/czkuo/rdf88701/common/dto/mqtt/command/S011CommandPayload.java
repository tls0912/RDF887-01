package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S011 指令：人員觸發開啟安全門
 * 廠商主動通知 ASE 有人觸發安全門開啟需求，
 * ASE 需驗證該人員是否具有開門資格。
 */
@Data
public class S011CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S011" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，用於對應請求與回覆 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "OPEN_DOOR_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體內容 */
    @JsonProperty("MESSAGE")
    private S011CommandPayload.Message message;

    /** 執行結果欄位，發送時為空字串，由回覆方填入（例如 OK / NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明，發送時為空，可由回覆方填入補充文字 */
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
