package com.czkuo.rdf88701.domain.dto.zip.CardReader;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CardReaderSecondaryBody {
    @JsonProperty("ResultInfo") private ResultInfo resultInfo;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;           // 0 or error
        @JsonProperty("ResultMessage") private String resultMessage; // "工號,姓名,站點,錯誤訊息" 空白填 NA
    }
}
