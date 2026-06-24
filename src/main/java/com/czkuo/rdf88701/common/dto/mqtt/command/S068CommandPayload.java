package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S068 指令：打帶前狀態確認（由廠商主動傳送至 ASE）
 * 用於請求確認當前設備是否允許進行打帶作業。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S068CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S068" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（時間戳 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAPING_MACHINE_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 處理結果（由 ASE 回傳填入） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，例如錯誤原因，可為空字串 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
