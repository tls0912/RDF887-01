package com.czkuo.rdf88701.common.dto.mqtt.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * U020 - Output WIP 架人員取貨請求指令 Payload
 * ASE → 廠商：通知需要取貨的 WIP 批號亮燈
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class U020CommandPayload {

    /** 指令類型，固定為 UNLOAD */
    @JsonProperty("CMD")
    private String cmd;

    /** 指令代碼，固定為 U020 */
    @JsonProperty("CMD_ID")
    private String cmdId;

    /** 訊息識別碼（yyyyMMddHHmmssSSS） */
    @JsonProperty("TID")
    private String tid;

    /** 指令說明，固定為 OUTPUT_WIP_GET_TRAY */
    @JsonProperty("ID_DESC")
    private String idDesc;

    /** 批號資訊清單 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果欄位，發送時為空 */
    @JsonProperty("RESULT")
    private String result;

    /** 補充說明（如錯誤原因） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 需要亮燈的批號清單 */
        @JsonProperty("LOT_LIST")
        private List<LotInfo> lotList;

        @Data
        public static class LotInfo {

            /** 批號（LOT ID） */
            @JsonProperty("LOT_ID")
            private String lotId;
        }
    }
}
