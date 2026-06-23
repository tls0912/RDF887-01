package com.czkuo.rdf88701.domain.dto.zip.DispatchOrder;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DispatchOrderSecondaryBody {
    @JsonProperty("ResultInfos") private List<ResultInfo> resultInfos;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;        // 0,22,104,>100
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
