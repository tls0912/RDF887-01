package com.czkuo.rdf88701.domain.dto.zip.StatusReport;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StatusReportSecondaryBody {
    @JsonProperty("ResultInfos") private java.util.List<ResultInfo> resultInfos;

    @Data
    public static class ResultInfo {
        @JsonProperty("Result")        private int result;
        @JsonProperty("ResultMessage") private String resultMessage;
    }
}
