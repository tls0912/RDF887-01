package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S014 指令：零件預警清單（由 SAA 傳送至 SEEC）
 * 每日早上與晚上定時更新
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S014CommandPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S014 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 指令描述，固定為 "TOOL_REMIND_LIST" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務識別碼（對應發送端的 TID） */
    @JsonProperty("TID")
    private String tid;

    /** 執行結果（傳送時可為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充結果說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
