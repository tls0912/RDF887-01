package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S045 指令：安全 Sensor 狀態請求（由 ASE 主動發出）
 * ASE 傳送空白指令給廠商，表達「請回報目前安全感測器狀態」
 * 此指令不包含 MESSAGE 區段，僅用來觸發對方主動回報
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S045CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S045" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SAFETY_DEVICE_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 結果欄位（發送時為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明（預設空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
