package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * R008 指令：通知從機台A搬貨到 WIP(STK)
 * - SAA→SEEC：MESSAGE 需帶 STK_PORT
 * - ASE→廠商：MESSAGE 禁止帶 STK_PORT
 * <p>
 * 用於啟動 AMR 搬運任務或外部派工
 */
@Data
public class R008CommandPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R008" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 任務識別碼，格式為 yyyyMMddHHmmssSSS
     * 同一批貨的後續事件（ACK/多階段回報）皆需用原始下達時的 TID
     */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ROBOT_MOVE_SCH_TO_WIP" */
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
     * R008 MESSAGE 欄位說明：
     * - SAA→SEEC 時 STK_PORT 必填
     * - ASE→廠商時禁止帶 STK_PORT（null 即可，會自動不序列化）
     */
    @Data
    public static class Message {

        /** 批號，搬運批次唯一識別（必填，例："252K8EV003"） */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 被搬運的載具／批號 ID（必填，例："252K8EV003_P_12"） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /**
         * 儲格位置（WIP 名稱，可為 null）
         * - 例：內部庫位名稱；若未指定則傳 null
         */
        @JsonProperty("WIPNAME")
        private String wipName;

        /** 目的地的機台名稱（必填，例："E0061380"） */
        @JsonProperty("DEST_LOC")
        private String destLoc;

        /** 目的設備 Port 名稱（必填，例："X1"） */
        @JsonProperty("EQP_PORT")
        private String eqpPort;

        /**
         * 盤高（例："5.62"）
         * - 使用 BigDecimal 可容納小數；若對方傳字串，Jackson 仍可解析。
         */
        @JsonProperty("TRAY_HIGH")
        private BigDecimal trayHigh;

        /**
         * 托盤型號／料號（必填；例："4610570101"）
         * - 依對接方定義填入
         */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /**
         * 托盤類型（必填；例："G" / "B"/ "E"）
         * - 依對接方定義填入
         */
        @JsonProperty("BIN_TYPE")
        private String binType;

        /**
         * 托盤數量（必填；以字串表達整數，例："10"）
         */
        @JsonProperty("TRAY_NUM")
        private Integer trayNum;

        /**
         * 要指定車子才會放（可為空字串或 null；例："" 或 "AMR01"）
         * - 不指定則交由調度端決策
         */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /**
         * 任務優先權（數字越大，優先度越高；可為 null；例："1"）
         */
        @JsonProperty("MOVE_PRIORITY")
        private Integer movePriority;

        /**
         * 此任務 AMR 的走的總哩程數（本任務里程，可為 null；例："123.4"）
         */
        @JsonProperty("MISSION_TRIP")
        private String missionTrip;

        /**
         * AMR 的目前總哩程數（累積里程，可為 null；例："12345.6"）
         */
        @JsonProperty("ODO")
        private BigDecimal odo;

        /**
         * 底車的速度（可為 null；例："1.2"）
         */
        @JsonProperty("AMR_SPEED")
        private BigDecimal amrSpeed;

        /**
         * 車子手臂的速度（可為 null；例："0.8"）
         * - 線上鍵名為 AMR_ROBOT_SPEED；亦接受別名 ROBOTIC_ARM_SPEED
         */
        @JsonProperty("AMR_ROBOT_SPEED")
        private BigDecimal amrRobotSpeed;

        /**
         * 包體尺寸（可為 null；例："10.9X10.9X9"）
         * - 字串格式依對接方約定（長X寬X高）
         */
        @JsonProperty("PPKG_BODY_SIZE")
        private String ppkgBodySize;

        /**
         * 起始的 STK Port
         * - SAA→SEEC 必填
         * - ASE→廠商時禁止出現（設為 null 即可不序列化）
         * 例："STK01" ~ "STK05"
         */
        @JsonProperty("STK_PORT")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String stkPort;
    }
}
