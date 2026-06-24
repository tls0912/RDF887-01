package com.czkuo.rdf88701.domain.dto.zip.StockerOutput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StockerOutputPrimaryBody {
    @JsonProperty("MESSAGE") private Message message;

    @Data
    public static class Message {
        @JsonProperty("BARCODE") private String barcode; // 或
        @JsonProperty("CARRIED") private String carried;
    }
}
