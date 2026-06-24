package com.czkuo.rdf88701.common.dto.mqtt.ack;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S045 回覆格式：確認接收安全 Sensor 狀態資料
 * 廠商回覆 ASE 傳來的設備狀態是否成功接收與處理
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S045AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S045" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS（需與請求一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SAFETY_DEVICE_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回覆訊息內容，可選填回傳清單或簡單確認 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果，例如 OK / FAIL */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明，例如失敗原因或接收狀態（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段（可帶回接收哪些設備狀態）
     */
    @Data
    public static class Message {

        /** 已接收設備狀態回覆清單（可選填） */
        @JsonProperty("SAFETY_DEVICE_LIST")
        private List<SafetyDeviceStatus> safetyDeviceList;
    }

    /**
     * 回覆用的安全設備項目
     */
    @Data
    public static class SafetyDeviceStatus {

        /** 裝置代碼（如 "B"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 狀態（如 OK / NG） */
        @JsonProperty("DEVICE_STATUS")
        private String deviceStatus;

        /** 狀態說明 */
        @JsonProperty("STATUS_DESCRIPTION")
        private String statusDescription;
    }
}
