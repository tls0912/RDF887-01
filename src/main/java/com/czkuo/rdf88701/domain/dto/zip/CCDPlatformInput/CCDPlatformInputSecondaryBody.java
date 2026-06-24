package com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class CCDPlatformInputSecondaryBody {
    @JsonProperty("ResultInfo") private CCDPlatformInputSecondaryBody.ResultInfo resultInfo;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;           // 0 or error
        @JsonProperty("ResultMessage") private String resultMessage; // "錯誤訊息" 空白填 NA
    }
}
