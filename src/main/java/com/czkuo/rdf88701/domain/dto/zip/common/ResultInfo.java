package com.czkuo.rdf88701.domain.dto.zip.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultInfo {
    @JsonProperty("Result")        private Integer result = 107; // 預設 OTHER_ERROR
    @JsonProperty("ResultMessage") private String resultMessage = "";
}
