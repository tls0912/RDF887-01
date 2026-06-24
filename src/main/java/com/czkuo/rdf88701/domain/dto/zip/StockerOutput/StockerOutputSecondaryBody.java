package com.czkuo.rdf88701.domain.dto.zip.StockerOutput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class StockerOutputSecondaryBody {
    @JsonProperty("ResultInfo") private ResultInfo resultInfo;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,104,>100
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
