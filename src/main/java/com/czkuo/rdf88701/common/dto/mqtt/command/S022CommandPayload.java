package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S022 指令：詢問系統控制狀態（Control State）
 * ASE 向廠商詢問當前控制模式（例如 LOCAL、REMOTE）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S022CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S022" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SYSTEM_CONTRAL_STATUS"（請注意拼字為 Contral） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 訊息主體，請求方可為 null，回覆時由對方填入 */
    @JsonProperty("MESSAGE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Message message;

    /** 指令執行結果（可為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明（可為空） */
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
