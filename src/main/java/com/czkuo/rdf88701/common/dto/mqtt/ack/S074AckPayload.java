package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * S074 任務查詢回覆 Payload
 * 廠商 → ASE：回傳目前所有待處理任務清單（如：R007、R008、R029、R030）
 */
@Data
public class S074AckPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S074 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID（對應請求的 TID） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 MISSION_LIST */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 回傳內容（任務列表） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 處理結果，例如 OK / NG */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明文字（錯誤或成功訊息） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 任務列表 */
        @JsonProperty("MISSION_LIST")
        private List<MissionItem> missionList;
    }

    @Data
    public static class MissionItem {

        /** 指令任務代碼（例如 R007_$TID） */
        @JsonProperty("COMMAND_TID")
        private String commandTid;

        /** 批號或 LOT ID */
        @JsonProperty("LOT_ID")
        private String lotId;

        /**
         * 任務狀態：
         * 儲位代碼 → 表示尚未搬運
         * STK → 表示 STK 正在搬運
         * AMR → 表示 AMR 正在搬運
         */
        @JsonProperty("STATUS")
        private String status;

        /** 設備名稱（如有） */
        @JsonProperty("EQPNAME")
        private String eqpName;

        /** 設備 Port 編號（如有） */
        @JsonProperty("EQP_PORT")
        private String eqpPort;
    }
}
