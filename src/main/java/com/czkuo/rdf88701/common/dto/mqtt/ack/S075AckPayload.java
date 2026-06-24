package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S075 任務狀態查詢回覆 Payload
 * 廠商 → ASE：回覆單一任務的當前狀態（含設備名稱與 port）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S075AckPayload {

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

    /** 回覆內容（任務狀態） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（如 OK / NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果訊息補充（如錯誤原因） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 查詢任務的指令代碼，例如：R007 */
        @JsonProperty("COMMOND")
        private String command;

        /** 查詢任務的原始 TID */
        @JsonProperty("TID")
        private String tid;

        /**
         * 任務目前狀態：
         * - 儲位 ID：表示尚在儲位
         * - STK：表示 STK 正在搬運中
         * - AMR：表示 AMR 正在搬運中
         * - NULL：無此任務
         */
        @JsonProperty("STATUS")
        private String status;

        /** 設備名稱（如有） */
        @JsonProperty("EQPNAME")
        private String eqpName;

        /** 設備 Port（如有） */
        @JsonProperty("EQP_PORT")
        private String eqpPort;
    }
}
