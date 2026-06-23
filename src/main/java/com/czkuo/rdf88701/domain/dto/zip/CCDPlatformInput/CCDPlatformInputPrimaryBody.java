package com.czkuo.rdf88701.domain.dto.zip.CCDPlatformInput;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CCDPlatformInputPrimaryBody {
    @JsonProperty("MESSAGE") private CCDPlatformInputPrimaryBody.Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("Name")         private String name;
        @JsonProperty("CARRIED")      private String carried;
        @JsonProperty("Status")       private int status;
    }
}
