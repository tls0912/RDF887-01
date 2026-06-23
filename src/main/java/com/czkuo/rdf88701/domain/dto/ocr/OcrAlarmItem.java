package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 設備警報項目 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrAlarmItem {
    @JsonProperty("alarmCode")    private String alarmCode;
    @JsonProperty("message")      private String message;
    @JsonProperty("occurredTime") private LocalDateTime occurredTime;
}
