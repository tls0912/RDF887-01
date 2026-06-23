package com.czkuo.rdf88701.domain.dto.zip.StatusQuery;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class StatusQueryPrimaryBody {
    @JsonProperty("QueryInfos") private List<QueryInfo> queryInfos;

    @Data
    public static class QueryInfo {
        @JsonProperty("Type") private int type; // 0..6
        @JsonProperty("Name") private String name;
    }
}
