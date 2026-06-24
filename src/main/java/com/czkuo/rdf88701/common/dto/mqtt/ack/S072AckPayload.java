package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.czkuo.rdf88701.common.dto.mqtt.command.S072CommandPayload;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S072 回覆資料 (ASE → 廠商)
 * 回應 Tray 間隙檢查結果
 * - RESULT: "OK"（確認 PASS）/ "NG"（確認 FAIL）
 * - RESULT_MESSAGE: 失敗原因或補充說明
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S072AckPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S072 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID（對應原始請求） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明（建議為 TRAY_GAP_CHECK，與請求一致） */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 影像與基本資訊載體 */
    @JsonProperty("MESSAGE")
    private S072CommandPayload.Message message;

    /** 執行結果（由 ASE 回填：OK/NG） */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明或錯誤訊息（由 ASE 回填） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 載具 ID */
        @JsonProperty("CARRIERID")
        private String carrierId;

        /** 批號 / 生產批次 */
        @JsonProperty("LOT_ID")
        private String lotId;

        /** Tray 類型代碼（例如 4606476111） */
        @JsonProperty("TRAY_TYPE")
        private String trayType;

        /** PORT 口 */
        @JsonProperty("LOCATION")
        private String location;

        /** Tray 左側影像資料（byte[]） */
        @JsonProperty("TRAY_LEFT_IMAGE")
        private byte[] trayLeftImage;

        /** Tray 右側影像資料（byte[]） */
        @JsonProperty("TRAY_RIGHT_IMAGE")
        private byte[] trayRightImage;
    }
}
