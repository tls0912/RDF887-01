package com.czkuo.rdf88701.domain.dto.zip.checktimer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CheckTimerSecondaryBody {
    @JsonProperty("ResultInfo") private ResultInfoWrapper resultInfo;

    @Data
    public static class ResultInfoWrapper {
        @JsonProperty("Result")        private int result;
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
