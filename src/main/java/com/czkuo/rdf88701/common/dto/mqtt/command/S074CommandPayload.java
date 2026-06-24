package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S074 任務查詢請求 Payload
 * ASE → 廠商：詢問目前系統中尚未完成的搬運任務清單
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S074CommandPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S074 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID（時間戳 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 MISSION_LIST */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回應結果（僅在回覆中使用） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（僅在回覆中使用） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
