package com.czkuo.rdf88701.domain.dto.zip.WipInfoUpdate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WipInfoUpdatePrimaryBody {
    @JsonProperty("MESSAGE") private Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("WIPNAME")         private String wipName;
        @JsonProperty("CARRIERID")      private String carrierId;
        @JsonProperty("LOT_ID")       private String lotId;
    }
}
