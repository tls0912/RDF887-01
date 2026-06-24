package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A013 指令：AGV 離開換電站（SEEC → SAA）
 * 回報 AGV 更換電池後離站的狀態資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class A013CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A013" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（格式 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "AGV_LEAVE_POWER_STATION" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 狀態內容（含里程與電池資訊） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回傳結果（通常為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充訊息（通常為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * AGV 離站時的狀態資訊
     */
    @Data
    public static class Message {

        /** AGV 車輛 ID（如 AGV01） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 換電後所使用的電池 ID（如 A001） */
        @JsonProperty("BATTERY_ID")
        private String batteryId;

        /** 電量百分比（如 90） */
        @JsonProperty("BATTERY_VALUE")
        private String batteryValue;

        /** 總里程（ODO）= AGV 累積行駛距離 */
        @JsonProperty("ODO")
        private String odo;

        /**
         * TRIP = 從離開電站到再次回到電站的里程
         * - 每次離站時歸零
         */
        @JsonProperty("TRIP")
        private String trip;
    }
}
