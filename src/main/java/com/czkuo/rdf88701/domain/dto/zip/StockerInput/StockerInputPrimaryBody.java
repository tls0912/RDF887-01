package com.czkuo.rdf88701.domain.dto.zip.StockerInput;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StockerInputPrimaryBody {
    @JsonProperty("MESSAGE") private Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("BARCODE")      private String barcode; // 或
        @JsonProperty("CARRIED")      private String carried; // 二擇一必填
    }
}
