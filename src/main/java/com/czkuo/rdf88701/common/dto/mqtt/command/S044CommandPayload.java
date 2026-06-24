package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S044 指令：查詢安全 Sensor 清單
 * ASE 發送查詢，要求廠商提供目前系統具備的安全設備列表
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S044CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S044" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SAFETY_DEVICE_LIST_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 請求端不帶 MESSAGE，由回覆端填入設備清單 */
    @JsonProperty("MESSAGE")
    private Object message; // 可為 null

    /** 回覆結果（如 OK、NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明文字（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
