package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.czkuo.rdf88701.common.dto.mqtt.command.S016CommandPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S016 指令的回覆格式（Acknowledgement）
 * 用於回應系統校時執行結果
 */
@Data
public class S016AckPayload {

    /** 指令主類型，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S016 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼（對應發送端的 TID） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 SYSTEM_TIMING */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 時間資訊 */
    @JsonProperty("MESSAGE")
    private S016AckPayload.Message message;

    /** 執行結果（OK / FAIL） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（可為空或錯誤描述） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 要同步的時間，格式為 yyyyMMddHHmmss */
        @JsonProperty("DATETIME")
        private String datetime;
    }
}
