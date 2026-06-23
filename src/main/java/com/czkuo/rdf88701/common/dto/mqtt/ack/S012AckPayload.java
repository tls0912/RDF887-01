package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S012 回覆格式：回應是否允許關閉安全門與驗證人員清單
 * ASE 回覆是否可由指定人員進行關門動作
 * <p>
 * RESULT:
 * - OK 表示允許關門
 * - NG 表示不允許關門
 */
@Data
public class S012AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S012" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，與請求對應 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "CLOSE_DOOR_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回覆的主體內容（包含可執行關門之人員） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回覆結果（OK / NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：人員工號清單
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 各機構區安全門名稱 */
        @JsonProperty("SAFE_DOOR_NAME")
        private String safeDoorName;

        /** 參與關門的人員工號清單 */
        @JsonProperty("STAFF_LIST")
        private List<String> staffList;
    }
}
