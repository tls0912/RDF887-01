package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S075 任務狀態查詢請求 Payload
 * ASE → 廠商：查詢單一任務的目前執行情況
 */
@Data
public class S075CommandPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S075 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID（格式：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 MISSION_STATUS_CHECK */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務狀態查詢參數 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回應結果（非請求端使用） */
    @JsonProperty("RESULT")
    private String result;

    /** 回應訊息補充（非請求端使用） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 指令名稱，例如：R007、R008 */
        @JsonProperty("COMMOND")
        private String command;

        /** 對應任務的 TID（R007/TID 對應） */
        @JsonProperty("TID")
        private String tid;
    }
}
