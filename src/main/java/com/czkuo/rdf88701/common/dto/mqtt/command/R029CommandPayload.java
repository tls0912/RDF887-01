package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * R029 - 通知將貨搬去拆併打帶請求 Payload
 * ASE → 廠商：通知開始執行拆批打帶任務
 */
@Data
public class R029CommandPayload {

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

    /** 任務內容 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 回應結果：預設為空，實際由接收端回覆 */
    @JsonProperty("RESULT")
    private String result;

    /** 錯誤或補充訊息（當 RESULT 為 NG 時會使用） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 要拆併打帶的批號清單 */
        @JsonProperty("CARRIER_LIST")
        private List<CarrierInfo> carrierList;

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
