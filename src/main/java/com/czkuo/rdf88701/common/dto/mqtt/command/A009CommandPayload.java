package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * A009 指令：詢問 AGV 車輛狀態（由 SAA 傳送至 SEEC）
 * 通常不含 MESSAGE 欄位
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class A009CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A009" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（格式 yyyyMMddHHmmssSSS，具延續性） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "AGV STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（通常為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充訊息（預設為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
