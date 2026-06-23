package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S015 指令的回覆格式（Acknowledgement）
 * 回應零件預警設定結果（成功 / 失敗）
 */
@Data
public class S015AckPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S015 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼（對應發送端的 TID） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 TOOL_REMIND_SETTING */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 執行結果（OK / FAIL） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（可為空或錯誤描述） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;
}
