package com.czkuo.rdf88701.domain.dto.zip.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Header {
    @JsonProperty("EventName") private String eventName;
    @JsonProperty("Direction") private String direction; // Primary / Secondary
    @JsonProperty("Sender")    private String sender;
    @JsonProperty("SendTime")  private String sendTime;  // yyyyMMddHHmmssSSS
}
