package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S002 指令：系統心跳確認（雙向皆可主動發送）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S002CommandPayload {

    /** 指令類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S002 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼，用於追蹤心跳訊息（格式：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 CHECK_READY */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（初始為空字串） */
    @JsonProperty("RESULT")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String result;

    /** 補充說明（初始為空字串，可為 null） */
    @JsonProperty("RESULT_MESSAGE")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String resultMessage;
}
