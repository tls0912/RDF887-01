package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S011 回覆格式：回應是否允許開啟安全門，以及通過驗證的人員清單
 * 用於 ASE 回覆廠商對 S011 開門請求的處理結果
 *
 * RESULT 值範例：
 * - OK：允許開門
 * - NG：不允許開門
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S011AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S011" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，需與原請求一致 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "OPEN_DOOR_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 驗證結果資料（包含成功的人員工號列表） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 驗證結果：OK（允許開門） / NG（拒絕開門） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果說明（可為空字串，或補充 NG 原因） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：人員工號列表
     */
    @Data
    public static class Message {

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 各機構區安全門名稱 */
        @JsonProperty("SAFE_DOOR_NAME")
        private String safeDoorName;

        /** 通過驗證的人員工號清單（例如 ["E12345", "E23456"]） */
        @JsonProperty("STAFF_LIST")
        private List<String> staffList;
    }
}
