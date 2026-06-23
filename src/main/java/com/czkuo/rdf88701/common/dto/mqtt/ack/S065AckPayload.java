package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S065 回覆格式：標籤資訊列印回應
 * 廠商回傳印製處理結果
 */
@Data
public class S065AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S065" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（與請求對應） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAG_INFO" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回應結果，如 OK / NG */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明（可為空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
