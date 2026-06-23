package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


/**
 * R030 指令：通知從 E-Rack 搬貨至機台
 * 由 SAA 傳送至 SEEC，用於啟動 AGV 移載任務
 */
@Data
public class R030CommandPayload {

    /** 指令主類型，固定為 "ROBOT" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "R030" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務識別碼，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "ROBOT_MOVE_SCH_FROM_ERACK_TO_EQP" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務細節 */
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

    @Data
    public static class Message {

        /** 批號 */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** 搬運對象（如料盤ID） */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 儲格位置（WIP 名稱） */
        @JsonProperty("WIPNAME")
        private String wipName;

        /** 目的地設備代碼（機台名稱） */
        @JsonProperty("DEST_LOC")
        private String destLoc;

        /** 機台接收 Port，例如 "X1" */
        @JsonProperty("EQP_PORT")
        private String eqpPort;

        /** 指定執行任務的 AGV 名稱 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 底車速度 */
        @JsonProperty("AGV_SPEED")
        private String agvSpeed;

        /** 車子手臂速度 */
        @JsonProperty("ROBOTIC_ARM_SPEED")
        private String roboticArmSpeed;

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
