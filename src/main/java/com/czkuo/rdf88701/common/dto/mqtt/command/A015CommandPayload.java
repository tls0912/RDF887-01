package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


/**
 * A015 指令：AGV 到達 EQP
 * - 由 SEEC 發送給 SAA，通知 AGV 抵達機台位置，等待 SAA 執行關閉光閘
 */
@Data
public class A015CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A015" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，具有延續性（來自原始派貨 TID） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "AGV_ARRIVAL_EQP"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令內容：AGV 到達相關資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 執行結果欄位，發送時為空字串，由接收方（回覆方）填入，例如 "OK"、"DONE" 等狀態代碼
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果說明，發送時為空字串，可由接收方填入補充說明文字，例如錯誤原因或其他訊息
     */
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
