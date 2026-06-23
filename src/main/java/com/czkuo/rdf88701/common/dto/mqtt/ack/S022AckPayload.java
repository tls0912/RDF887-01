package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S022 回覆格式：設備控制狀態（Control State）
 * 廠商回覆 ASE 詢問的設備控制模式狀態，
 * 例如：REMOTE（遠端控制）、LOCAL（本地控制）
 */
@Data
public class S022AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S022" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，與請求方相同 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SYSTEM_CONTRAL_STATUS"（拼字依原協議） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 控制狀態內容，包括設備名稱與狀態值 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回覆結果（通常為 OK；也可為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明，可為空字串或失敗原因 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：設備控制狀態資訊
     */
    @Data
    public static class Message {

        /** 設備名稱（如 "STK", "ROBOT"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 控制狀態（如 "REMOTE", "LOCAL", "MANUAL"） */
        @JsonProperty("STATUS")
        private String status;
    }
}
