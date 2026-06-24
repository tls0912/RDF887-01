package com.czkuo.rdf88701.domain.dto.zip.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultInfo {
    @JsonProperty("Result")        private Integer result = 107; // 預設 OTHER_ERROR
    @JsonProperty("ResultMessage") private String resultMessage = "";
}
