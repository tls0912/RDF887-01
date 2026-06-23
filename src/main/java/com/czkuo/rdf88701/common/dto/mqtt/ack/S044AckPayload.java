package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S044 回覆格式：提供安全 Sensor 裝置清單
 * 廠商回覆目前系統中的安全設備名稱與功能說明
 */
@Data
public class S044AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S044" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "SAFETY_DEVICE_LIST_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回覆內容，包含多筆安全 Sensor 清單 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回覆結果（如 OK、NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 結果補充說明（可為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：包含多筆安全 Sensor 資訊
     */
    @Data
    public static class Message {

        /** 安全裝置清單（如光閘、緊急開關、安全門等） */
        @JsonProperty("SAFETY_DEVICE_LIST")
        private List<SafetyDevice> safetyDeviceList;
    }

    /**
     * 安全裝置項目定義
     */
    @Data
    public static class SafetyDevice {

        /** 裝置代號（由廠商自定，如 "B"、"C"、"D"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 裝置說明（如 "光閘"、"緊急開關"） */
        @JsonProperty("DEVICE_DESCRIPTION")
        private String deviceDescription;
    }
}
