package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** 建立任務：Response */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrCreateTaskResponse {
    @JsonProperty("accepted")  private Boolean accepted;
    @JsonProperty("message")   private String message;
    @JsonProperty("errorCode") private String errorCode; // DEVICE_BUSY / INVALID_DATA / INTERNAL_ERR / UNAUTHORIZED
}
