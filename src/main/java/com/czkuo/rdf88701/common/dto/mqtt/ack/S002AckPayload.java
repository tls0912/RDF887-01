package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S002 指令的回覆格式（Acknowledgement）
 */
@Data
public class S002AckPayload {

    /** 指令類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S002 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 原請求對應的任務 TID */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "CHECK_READY"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回覆結果，例如 OK / FAIL */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，可空 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
