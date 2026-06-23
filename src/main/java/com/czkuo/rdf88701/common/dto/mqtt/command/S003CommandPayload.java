package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S003 指令：系統初始化指令 Payload
 * 由 ASE 發送至廠商，用於通知啟動初始化流程。
 */
@Data
public class S003CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S003" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "INITIAL_START" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（初始為空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（初始為空字串，可為 null） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
