package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S066 指令的回覆格式（ACK）
 * 廠商回覆標籤印製請求的處理結果。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S066AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S066" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，與對應請求相同 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAG_INFO" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 處理結果，例如 OK、FAIL */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，可為空字串或錯誤訊息 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
