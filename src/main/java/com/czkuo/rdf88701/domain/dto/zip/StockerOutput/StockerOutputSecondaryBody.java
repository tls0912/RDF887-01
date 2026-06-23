package com.czkuo.rdf88701.domain.dto.zip.StockerOutput;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StockerOutputSecondaryBody {
    @JsonProperty("ResultInfo") private ResultInfo resultInfo;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,104,>100
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
