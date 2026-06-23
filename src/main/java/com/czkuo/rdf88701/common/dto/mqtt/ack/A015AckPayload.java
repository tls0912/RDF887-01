package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.czkuo.rdf88701.common.dto.mqtt.command.A015CommandPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A015 指令的回覆格式（Acknowledgement）
 * - 由 SAA 回覆給 SEEC，告知是否已完成光閘關閉作業
 */
@Data
public class A015AckPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A015" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 回應所對應的任務代碼 TID（需與收到的請求一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令內容：AGV 到達相關資訊 */
    @JsonProperty("MESSAGE")
    private A015AckPayload.Message message;

    /** 執行結果（例如 "DONE" 表示光閘關閉完成，或 "OK" 表示收到） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（可選，例如 "光閘已關閉"、"光閘感測器異常"） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：AGV 到達時的具體資訊
     */
    @Data
    public static class Message {

        /** 此欄位重複填入 TID，作為派令盒號追蹤使用 */
        @JsonProperty("TID")
        private String tid;

        /** 到達的 AGV 裝置名稱（如 "AGV01"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 目的地位置，例如 EQP 名稱或 STK 儲位（如 "EQP01"、"STK03"） */
        @JsonProperty("DEST_LOC")
        private String destLoc;
    }
}
