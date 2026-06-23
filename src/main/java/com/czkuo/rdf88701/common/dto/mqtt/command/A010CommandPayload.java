package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * A010 指令：AGV 狀態定時回拋（SEEC → SAA）
 * 回傳多台 AGV 當前狀態與正在執行的任務
 */
@Data
public class A010CommandPayload {

    /** 指令類型，固定為 "AGV" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "A010" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼（格式 yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ALL AGV STATUS REPLAY" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回報型態（固定為 "ACTION"） */
    @JsonProperty("REPLAY")
    private String replay;

    /** AGV 狀態資料內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回傳狀態（通常為空） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（通常為空） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 多台 AGV 狀態清單 */
        @JsonProperty("DATA")
        private List<AgvStatus> data;
    }

    @Data
    public static class AgvStatus {

        /** AGV 車輛 ID（如 AGV01） */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 當前座標位置（如 "125,125"） */
        @JsonProperty("CURRENT_LOC")
        private String currentLoc;

        /** 主狀態（如 "2" 表示運行中） */
        @JsonProperty("MAIN_STATUS")
        private String mainStatus;

        /** 子狀態（如 "4" 表示執行中） */
        @JsonProperty("SUB_STATUS")
        private String subStatus;

        /** 電池 ID（如 A008） */
        @JsonProperty("BATTERY_ID")
        private String batteryId;

        /** 電量百分比（原 JSON 拼為 BATTEY_VALUE） */
        @JsonProperty("BATTEY_VALUE")
        private String batteryValue;

        /** 車頭朝向角度（如 0、90、180、270） */
        @JsonProperty("AGV_ANGLE")
        private String agvAngle;

        /** 當前執行中的命令清單（可為多筆） */
        @JsonProperty("CURRENT_COMMAND")
        private List<Command> currentCommand;
    }

    @Data
    public static class Command {

        /** 任務代碼（如 R007_20230809163520） */
        @JsonProperty("COMMAND")
        private String command;

        /** 批號（可為空） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 指派時間（格式：yyyy-MM-dd HH:mm:ss） */
        @JsonProperty("ASSING_TIME")
        private String assingTime; // 拼字保留一致性
    }
}
