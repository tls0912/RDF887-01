package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S068 指令的回覆（ACK）
 * ASE 回覆是否允許進行打帶動作。
 * - OK: 可以打帶
 * - NG: 不可以打帶
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S068AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S068" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（與請求端相同） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "TAPING_MACHINE_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 處理結果，應為 OK 或 NG */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明，可為空字串或詳細說明 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
