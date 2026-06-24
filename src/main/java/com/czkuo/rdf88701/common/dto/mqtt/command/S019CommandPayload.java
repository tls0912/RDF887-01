package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S019 指令：Robot HMI 顯示來自 MCS 的訊息
 * ASE 發送顯示訊息給廠商（如安全門未關閉等），支援中英文顯示
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S019CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S019" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TERMINAL_Display" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 訊息內容（中英文） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（傳送時可為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：顯示訊息內容
     */
    @Data
    public static class Message {

        /** 英文訊息（如 "Security door is not closed"） */
        @JsonProperty("MSG_EN")
        private String msgEn;

        /** 中文訊息（如 "安全門未關閉"） */
        @JsonProperty("MSG_CH")
        private String msgCh;
    }
}
