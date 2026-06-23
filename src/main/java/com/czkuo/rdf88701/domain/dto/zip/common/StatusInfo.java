package com.czkuo.rdf88701.domain.dto.zip.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatusInfo {
    @JsonProperty("Type")   private Integer type;    // 0..6
    @JsonProperty("Name")   private String name;
    @JsonProperty("Status") private Integer status;
    @JsonProperty("Message")private java.util.List<String> message;
}