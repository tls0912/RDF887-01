package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S003 指令的回覆格式（Acknowledgement）
 * - 回應對方的初始化請求結果。
 * - 一般由廠商系統（接收端）回覆 RESULT = "OK" 或 "FAIL"
 */
@Data
public class S003AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S003" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 回應對應的任務識別碼（與原始 S003 指令一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "INITIAL_START" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回覆結果（例如 OK、FAIL） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（例如錯誤原因，可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
