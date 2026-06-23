package com.czkuo.rdf88701.domain.dto.zip.StatusReport;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class StatusReportPrimaryBody {
    @JsonProperty("StatusInfos") private List<StatusInfo> statusInfos;

    @Data
    public static class StatusInfo {
        @JsonProperty("Type")   private int type;
        @JsonProperty("Name")   private String name;
        @JsonProperty("Status") private int status;
        @JsonProperty("Message")private List<String> message;
    }
}
