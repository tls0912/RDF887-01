package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */


@Data
public class R030AckPayload {

    /** 指令類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R030" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤碼，格式為 yyyyMMddHHmmssSSS，唯一標示此筆任務 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ROBOT_MOVE_SCH_FROM_ERACK_TO_EQP" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務細節訊息，封裝搬運相關欄位 */
    @JsonProperty("MESSAGE")
    private Message message;

    /**
     * 任務執行結果狀態碼
     * 可能值：
     *   OK                    - 收到任務時回覆
     *   ASSIGN                - 指派給 AGV 時回覆
     *   START                 - 開始執行該任務時回覆
     *   ERACK_OUTPUT_START    - E-Rack 下貨開始
     *   ERACK_OUTPUT_END      - E-Rack 下貨完成
     *   END                   - 任務完成時回覆
     *   FAIL                  - 失敗（RESULT_MESSAGE 需填寫原因）
     *   CANCEL                - 任務取消
     */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明或錯誤原因，如指派給哪台 AGV 等 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 批號，任務識別用 */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 搬運物件 ID，通常為料盤或載具編號 */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 儲格名稱（WIP名稱） */
        @JsonProperty("WIPNAME")
        private String wipName;

        /** 目的地機台代碼 */
        @JsonProperty("DEST_LOC")
        private String destLoc;

        /** 目的地機台接收通訊埠，如 "X1" */
        @JsonProperty("EQP_PORT")
        private String eqpPort;

        /** 指定執行任務的 AGV 車輛名稱，如 "AGV01" */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** AGV 底盤移動速度，單位由系統定義 */
        @JsonProperty("AGV_SPEED")
        private String agvSpeed;

        /** AGV 手臂搬運速度，單位由系統定義 */
        @JsonProperty("ROBOTIC_ARM_SPEED")
        private String roboticArmSpeed;

        /**
         * 起始的 STK Port
         * - SEEC→SAA 必填
         * - SAA→ASE 禁止出現（設為 null 即可不序列化）
         * 例："STK01" ~ "STK05"
         */
        @JsonProperty("STK_PORT")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String stkPort;
    }
}
