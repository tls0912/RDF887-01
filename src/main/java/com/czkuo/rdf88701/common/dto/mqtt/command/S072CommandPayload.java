package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * S072 Tray 間隙檢查 指令 (TRAY_GAP_CHECK)
 * 廠商 → ASE：傳送 Tray 左/右影像與基本資訊，由 ASE 判斷是否通過檢查
 *
 * RESULT 規格（由 ASE 回填）：
 * - "OK"：確認 PASS
 * - "NG"：確認 FAIL
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class S072CommandPayload {

    /** 指令主類別，固定為 SYSTEM */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 S072 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 任務 ID，格式為 yyyyMMddHHmmssSSS */
    @JsonProperty("TID")
    private String tid;

    /**
     * 指令說明，依協議為 TRAY_GAP_CHECK（你的範例為 TRAT_GAP_CHECK，請按實際對接值帶入）
     * 這裡不做常值限制，僅映射字串
     */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 影像與基本資訊載體 */
    @JsonProperty("MESSAGE")
    private Message message;

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