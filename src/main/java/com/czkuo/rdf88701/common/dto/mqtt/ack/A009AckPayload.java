package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * A009 回覆格式：AGV 狀態回傳（由 SEEC 回傳多台 AGV 狀態）
 */
@Data
public class A009AckPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A009" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（應與原始 A009 請求相同） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "AGV STATUS" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回傳資料封裝區塊 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回傳結果（通常為 "OK"） */
    @JsonProperty("RESULT")
    private String result;

    /** 回傳補充訊息（通常為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * 回傳資料主體：封裝 AGV 多車狀態清單
     */
    @Data
    public static class Message {

        /** 多台 AGV 狀態清單 */
        @JsonProperty("DATA")
        private List<AgvStatus> data;
    }

    /**
     * 每一台 AGV 的狀態項目
     */
    @Data
    public static class AgvStatus {

        /** 車輛 ID（如 WGVA01） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 當前位置座標（例如 "555,123"） */
        @JsonProperty("CURRENT_LOC")
        private String currentLoc;

        /** 主狀態碼（如 1=Idle, 2=Moving） */
        @JsonProperty("MAIN_STATUS")
        private String mainStatus;

        /** 子狀態碼（如 1=Waiting, 4=Working） */
        @JsonProperty("SUB_STATUS")
        private String subStatus;

        /** 當前電池 ID（如 A004） */
        @JsonProperty("BATTERY_ID")
        private String batteryId;

        /** 電池電量百分比（如 "85"） */
        @JsonProperty("BATTERY_VALUE")
        private String batteryValue;

        /** 車頭朝向角度（如 0, 90, 180, 270） */
        @JsonProperty("AGV_ANGLE")
        private String agvAngle;
    }
}
