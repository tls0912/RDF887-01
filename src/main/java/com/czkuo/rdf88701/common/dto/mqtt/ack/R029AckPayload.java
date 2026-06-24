package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.czkuo.rdf88701.common.dto.mqtt.command.R029CommandPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * R029 - 通知將貨搬去拆併打帶回覆 Payload
 * 廠商 → ASE：回覆接收狀態、進度或錯誤資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class R029AckPayload {

    /** 指令類別：ROBOT */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼：R029 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 傳輸識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明：MOVE_LOTS_TO_DISMANTLE_AND_TIE */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 任務處理結果回傳內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 任務狀態：
     *  - OK：任務已接收
     *  - NG：接收失敗（須提供 RESULT_MESSAGE）
     *  - START：任務開始
     *  - END：任務完成
     */
    @JsonProperty("RESULT")
    private String result;

    /** 錯誤或補充說明（當 RESULT 為 NG 時） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 要拆併打帶的批號清單 */
        @JsonProperty("CARRIER_LIST")
        private List<R029AckPayload.CarrierInfo> carrierList;

        /** 批數統計 */
        @JsonProperty("COUNT")
        private String count;

        /** 托盤型號（Tray type 編碼） */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** 托盤描述（文字說明） */
        @JsonProperty("TRAY_DESC")
        private String trayDesc;
    }

    @Data
    public static class CarrierInfo {

        /** 批號（如 30UYY2V001_PASS_1） */
        @JsonProperty("CARRIERID")
        private String carrierId;
    }
}
