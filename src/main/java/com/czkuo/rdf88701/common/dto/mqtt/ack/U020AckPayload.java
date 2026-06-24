package com.czkuo.rdf88701.common.dto.mqtt.ack;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * U020 - Output WIP 架人員取貨回覆 Payload
 * 廠商 → ASE：回覆批號已處理或提示結束或錯誤
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class U020AckPayload {

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

    /** 批號處理結果 */
    @JsonProperty("MESSAGE")
    private Message message;

    /** 結果狀態：OK（亮燈成功）/ END（完成處理）/ FAIL（處理失敗） */
    @JsonProperty("RESULT")
    private String result;

    /** 錯誤說明（當 RESULT 為 FAIL 時） */
    @JsonProperty("RESULT_MESSAGE")
    private String resultMessage;

    @Data
    public static class Message {

        /** 回傳的批號清單（通常與發送一致） */
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
