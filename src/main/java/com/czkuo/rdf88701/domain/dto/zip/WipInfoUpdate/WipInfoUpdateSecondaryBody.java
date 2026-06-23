package com.czkuo.rdf88701.domain.dto.zip.WipInfoUpdate;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WipInfoUpdateSecondaryBody {
    @JsonProperty("ResultInfo") private ResultInfo resultInfo;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;           // 0 or error
        @JsonProperty("ResultMessage") private String resultMessage; // "錯誤訊息" 空白填 NA
    }
}
