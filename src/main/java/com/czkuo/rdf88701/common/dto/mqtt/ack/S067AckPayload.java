package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S067 回覆格式：電池資訊回拋
 * 廠商收到查詢後，回拋電池即時狀態清單
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S067AckPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S067" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼（需與查詢請求一致） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "BATTERY_STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 電池資訊清單 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果（OK / FAIL 等） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（如失敗原因等，可為空字串） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：多顆電池即時狀態
     */
    @Data
    public static class Message {

        /** 電池清單 */
        @JsonProperty("BATTERY_LIST")
        private List<BatteryInfo> batteryList;
    }

    /**
     * 電池資訊欄位（每一顆電池的即時狀態）
     */
    @Data
    public static class BatteryInfo {

        /** 電池 ID（如 "LA05"） */
        @JsonProperty("BATTERY_ID")
        private String batteryId;

        /** 電量百分比（如 "89.5"），有些系統用 BATTERY，有些用 BATTERY_VALUE，需相容處理 */
        @JsonProperty("BATTERY")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String battery;

        /** 電量百分比（如 "89.5"），有些系統用 BATTERY，有些用 BATTERY_VALUE，需相容處理 */
        @JsonProperty("BATTERY_VALUE")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String batteryValue;

        /** 電壓（單位 V，如 "4.7"） */
        @JsonProperty("BATTERY_VOLT")
        private String batteryVolt;

        /** 電流（單位 A，如 "2.0"） */
        @JsonProperty("BATTERY_AMP")
        private String batteryAmp;

        /** 溫度（單位 ℃，如 "32.0"） */
        @JsonProperty("BATTERY_TEMP")
        private String batteryTemp;

        /** 所屬裝置名稱（如 "AGV01"、"Power Swap Station"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;
    }
}
