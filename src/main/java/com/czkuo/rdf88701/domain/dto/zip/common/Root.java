package com.czkuo.rdf88701.domain.dto.zip.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Root<T> {
    @JsonProperty("Header") private Header header;
    @JsonProperty("Body")   private T body;
}
