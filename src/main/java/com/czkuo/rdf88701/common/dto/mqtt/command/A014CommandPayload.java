package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A014 指令：AGV 回到換電站
 * - 由 SEEC 發送給 SAA，通知 AGV 回到換電站，並提供電池與里程資訊
 */
@Data
public class A014CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A014" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，具延續性 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述（固定為 "AGV_ARRIVAL_POWER_STATION"） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令內容：AGV 換電資訊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 執行結果欄位，發送時為空字串，由接收方（回覆方）填入，例如 "OK"、"ASSIGN"、"FAIL" 等狀態代碼
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果說明，發送時為空字串，可由接收方填入補充說明文字，例如錯誤原因或其他訊息
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：AGV 換電時提供的狀態資料
     */
    @Data
    public static class Message {

        /** 裝置名稱（例如 AGV01） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 電池 ID（例如 A001） */
        @JsonProperty("BATTERY_ID")
        private String batteryId;

        /** 當下的電量百分比（例如 "30"） */
        @JsonProperty("BATTERY_VALUE")
        private String batteryValue;

        /** 總里程數（ODO） */
        @JsonProperty("ODO")
        private String odo;

        /** 本次從離開充電站至返回的里程（TRIP） */
        @JsonProperty("TRIP")
        private String trip;
    }
}
