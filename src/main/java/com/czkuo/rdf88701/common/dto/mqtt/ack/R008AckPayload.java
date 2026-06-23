package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * R008 回覆格式（Ack）：回覆機台A搬貨到WIP(STK)任務狀態
 * - 用於 SAA→SEEC、ASE→廠商 兩種場景
 * - MESSAGE 欄位內容與指令相同（須 echo 批號與設備資訊）
 */
@Data
public class R008AckPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R008" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /**
     * 任務識別碼，格式為 yyyyMMddHHmmssSSS
     * 必須與原下達的 TID 相同，確保一批貨可追蹤任務全流程
     */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ROBOT_MOVE_SCH_TO_WIP" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /**
     * 任務細節，需與指令方 MESSAGE 一致（可複製原內容）
     * - SAA→SEEC 時 STK_PORT 必帶
     * - ASE→廠商時 STK_PORT 禁止出現
     */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 任務回覆狀態
     * - OK：收到任務時回覆
     * - ASSIGN：指派給 AGV
     * - FAIL：失敗（需於 RESULT_MESSAGE 說明原因）
     * - CANCEL：任務取消
     * - 其他：見規格
     */
    @JsonProperty("RESULT")
    private String result;

    /**
     * 狀態補充說明，例如 AGV01（執行設備）、失敗原因、取消原因等
     * - 正常完成時可為設備名稱
     * - FAIL 時需說明原因
     */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * R008 Ack MESSAGE 物件
     * - 欄位定義與 Command MESSAGE 相同
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
         * 托盤高度（必填；以字串表達數值，例："5.62"）
         * - 建議來源以實際量測或工藝標準填入
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
