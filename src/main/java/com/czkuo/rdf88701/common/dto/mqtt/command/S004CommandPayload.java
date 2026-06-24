package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S004 指令：查詢 WIP 資料庫（由 ASE 發出）
 * - 目的為比對目前廠商儲位的 WIP 狀態
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S004CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S004" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼（格式：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "SYSTEM_COMPARE_DB"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（發送端為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（發送端為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
