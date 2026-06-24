package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * R007 指令：通知從 WIP(STK) 搬貨至機台
 * - SAA→SEEC：由 SAA 下達搬運命令給 SEEC，MESSAGE 需帶 STK_PORT
 * - ASE→廠商：由 ASE 外部系統派工，MESSAGE 禁止帶 STK_PORT
 * <p>
 * 用於啟動 AGV 移載任務或外部派工（依場景組包）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class R007CommandPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R007" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 任務識別碼，格式為 yyyyMMddHHmmssSSS
     * 同一批貨的後續事件（ACK/多階段回報）皆需用原始下達時的 TID
     */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ROBOT_MOVE_SCH_TO_EQP" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /**
     * 任務細節，封裝搬運相關所有欄位
     * 欄位依發送對象略有不同（STK_PORT 僅限內部場景出現）
     */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 執行結果欄位，Command 發送時必為空字串。
     * 由接收方（Ack）回覆，例如 "OK"、"ASSIGN"、"FAIL"、"CANCEL" 等
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 結果說明，補充錯誤原因或回覆說明。
     * Command 發送時必為空字串，Ack 回覆時依狀態填寫
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * R007 MESSAGE 欄位說明：
     * - SAA→SEEC 時 STK_PORT 必填
     * - ASE→廠商時禁止帶 STK_PORT（null 即可，會自動不序列化）
     */
    @Data
    public static class Message {

        /**
         * 批號，搬運批次唯一識別（必填，例："30UYY2V001"）
         */
        @JsonProperty("LOT_ID")
        private String lotId;

        /**
         * 被搬運的批號或載具 ID（必填，例："TY0001VM"）
         */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /**
         * 儲格位置（WIP 名稱，必填，例："1025"）
         */
        @JsonProperty("WIPNAME")
        private String wipName;

        /**
         * 目的地設備名稱（必填，例："E0061380"）
         */
        @JsonProperty("DEST_LOC")
        private String destLoc;

        /**
         * 目的設備 Port 名稱（必填，例："X1"）
         */
        @JsonProperty("EQP_PORT")
        private String eqpPort;

        /**
         * 盤高（例："5.62"）
         * - 使用 BigDecimal 可容納小數；若對方傳字串，Jackson 仍可解析。
         */
        @JsonProperty("TRAY_HIGH")
        private BigDecimal trayHigh;

        /** 盤型（例："" 或 "A/B/..."；不限制） */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** 盤數（例："10" → 10） */
        @JsonProperty("TRAY_NUM")
        private Integer trayNum;

        /**
         * 指定執行任務的 AGV 名稱（必填，例："AGV01"）
         */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 任務優先序（字串 "1"→整數 1；可選） */
        @JsonProperty("MOVE_PRIORITY")
        private Integer movePriority;

        /** 任務路徑/趟次（可空） */
        @JsonProperty("MISSION_TRIP")
        private String missionTrip;

        /** 里程（可空，小數） */
        @JsonProperty("ODO")
        private BigDecimal odo;

        /** 期望 AMR 速度（可空，小數，單位依協定） */
        @JsonProperty("AMR_SPEED")
        private BigDecimal amrSpeed;

        /** 期望機器人本體速度（可空，小數，單位依協定） */
        @JsonProperty("AMR_ROBOT_SPEED")
        private BigDecimal amrRobotSpeed;

        /** 包體尺寸（例："10.9X10.9X9"） */
        @JsonProperty("PPKG_BODY_SIZE")
        private String ppkgBodySize;

        /**
         * 翻轉標誌（"Y"/"N"）
         * 注意：鍵名依你現行協定為 "FLIP"，此處嚴格對齊不更名。
         */
        @JsonProperty("FLIP")
        private String flip;

        /**
         * 起始的 STK Port
         * - SAA→SEEC 必填
         * - ASE→SAA 時禁止出現（設為 null 即可不序列化）
         * 例："STK01" ~ "STK05"
         */
        @JsonProperty("STK_PORT")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String stkPort;
    }
}
