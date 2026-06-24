package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A008 指令：AGV 車事件（由 SEEC 傳送至 SAA）
 * 回報 AGV 當前狀態、任務執行情況、位置與電量等資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class A008CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A008" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼（格式為 yyyyMMddHHmmssSSS，具有延續性） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "AGV EVENT" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令內容區塊：AGV 狀態與任務資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果狀態（保留欄位，通常為空） */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 補充描述：
     * - 例如 AGV 離開站點："LEAVE_E0140314"
     * - 或離開儲位："LEAVE_STK01"
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：AGV 狀態與任務執行詳細資訊
     */
    @Data
    public static class Message {

        /** AGV 名稱（例如 "AGV01"） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 當前運作狀態（如 RUN、IDLE、ERROR） */
        @JsonProperty("STATUS")
        private String status;

        /** 指令識別碼（綁定原始任務指令） */
        @JsonProperty("COMMAND_ID")
        private String commandId;

        /** 搬運載具代碼，例如 11TY00V002_P_1 */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 電量百分比（例如 "85"） */
        @JsonProperty("BATTERY")
        private String battery;

        /** 目前位置（如 EQP、STK、POWER_STATION） */
        @JsonProperty("DEST_LOC")
        private String destLoc;

        /**
         * 任務狀態進度：
         * - INPUT_START、INPUT_END
         * - OUTPUT_START、OUTPUT_END
         */
        @JsonProperty("JOB_STATUS")
        private String jobStatus;
    }
}
