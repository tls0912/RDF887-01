package com.czkuo.rdf88701.domain.dto.zip.CancelDispatchOrder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CancelDispatchOrderSecondaryBody {
    @JsonProperty("ResultInfos") private java.util.List<ResultInfo> resultInfos;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,104,>100
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
