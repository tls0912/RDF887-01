package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S067 指令：電池資訊查詢（主動要求回拋）
 * ASE 發送查詢請求給廠商，要求回拋現有電池資訊
 */
@Data
public class S067CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S067" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼（格式：yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "BATTERY_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（此為查詢用，固定空字串） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（此為查詢用，固定空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
