package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S010 指令：人員刷卡驗證
 * 廠商主動傳送刷卡資訊至 ASE，請求進行工號認證
 * 用途範例：設備操作或門禁系統中需確認人員身分
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S010CommandPayload {

    /** 指令類型，固定為 "SYSTEM" */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 "S010" */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務追蹤代碼，格式為 yyyyMMddHHmmssSSS，用於對應請求與回覆 */
    @JsonProperty("TID")
    private String tid;

    /** 指令描述，固定為 "CARD_NUMBER_CHECK" */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 指令主體內容（刷卡工號） */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 執行結果欄位，初始傳送時為空字串，由回應方填入 */
    @JsonProperty("RESULT")
    private String result;

    /** 執行結果補充說明，可為空字串或錯誤描述文字 */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    /**
     * MESSAGE 區段：刷卡工號資訊
     */
    @Data
    public static class Message {

        /** 刷卡人員的工號（例如員工編號、卡號等） */
        @JsonProperty("CARD_NUMBER")
        private String cardNumber;

        /** 各機構區域 */
        @JsonProperty("DEVICE_NAME")
        private String deviceName;

        /** 各機構區安全門名稱 */
        @JsonProperty("SAFE_DOOR_NAME")
        private String safeDoorName;
    }
}
