package com.czkuo.rdf88701.domain.dto.zip.StockerInput;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StockerInputPrimaryBody {
    @JsonProperty("MESSAGE") private Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("BARCODE")      private String barcode; // 或
        @JsonProperty("CARRIED")      private String carried; // 二擇一必填
    }
}
