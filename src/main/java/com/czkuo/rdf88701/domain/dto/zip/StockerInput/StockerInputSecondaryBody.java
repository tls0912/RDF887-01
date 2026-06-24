package com.czkuo.rdf88701.domain.dto.zip.StockerInput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StockerInputSecondaryBody {
    @JsonProperty("MESSAGE")   private Message message;
    @JsonProperty("ResultInfo")private ResultInfo resultInfo;

    @Data
    public static class Message {
        @JsonProperty("BARCODE")      private String barcode;
        @JsonProperty("CARRIED")      private String carried;
        @JsonProperty("LOT_ID")       private String lotId;
        @JsonProperty("TRAY_HIGH")    private String trayHigh;
        @JsonProperty("TRAY_TYPE")    private String trayType;
        @JsonProperty("MESSAGE_TYPE") private String messageType;
    }

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,104,108,>100
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
