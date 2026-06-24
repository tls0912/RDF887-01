package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S019 回覆格式：Robot HMI 顯示訊息的確認結果
 * 廠商回覆是否成功接收並處理顯示訊息
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S019AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S019" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，需與原請求一致 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TERMINAL_Display" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 訊息內容（中英文訊息） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回覆結果（如 OK） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：顯示訊息內容
     */
    @Data
    public static class Message {

        /** 英文訊息 */
        @JsonProperty("MSG_EN")
        private String msgEn;

        /** 中文訊息 */
        @JsonProperty("MSG_CH")
        private String msgCh;
    }
}
