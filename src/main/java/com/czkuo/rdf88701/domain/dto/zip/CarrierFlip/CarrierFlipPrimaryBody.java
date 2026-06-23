package com.czkuo.rdf88701.domain.dto.zip.CarrierFlip;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CarrierFlipPrimaryBody {
    @JsonProperty("MESSAGE") private CarrierFlipPrimaryBody.Message message;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("Name")         private String name;
        @JsonProperty("CARRIED")      private String carried;
    }
}
