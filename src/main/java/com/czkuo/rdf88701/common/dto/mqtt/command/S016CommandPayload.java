package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S016 指令：系統校時（由 SAA 傳送至 SEEC）
 * 用於將主系統時間下發給子系統
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S016CommandPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S016 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 SYSTEM_TIMING */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 時間資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（傳送時可為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 要同步的時間，格式為 yyyyMMddHHmmss */
        @JsonProperty("DATETIME")
        private String datetime;
    }
}
