package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.czkuo.rdf88701.common.dto.mqtt.command.S021CommandPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S021 回覆格式：設備目前處理狀態（Process State）
 * 廠商回覆 ASE 查詢的設備名稱與狀態，例如 IDLE/RUN/ERROR
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S021AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S021" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，與指令一致 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SYSTEM_PROCESS_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 設備處理狀態內容（設備名稱與狀態） */
    @JsonProperty("MESSAGE")
    private List<S021AckPayload.Message> message;

    /** 回覆結果（通常為空字串或 OK） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（通常為空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * 設備狀態內容
     */
    @Data
    public static class Message {

        /** 設備名稱（如 "STK"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 狀態值（如 "IDLE", "RUN", "ERROR"） */
        @JsonProperty("STATUS")
        private String status;
    }
}
