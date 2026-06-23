package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 建立任務：Request */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrCreateTaskRequest {
    @JsonProperty("taskId")      private Long taskId;
    @JsonProperty("ocrDeviceId") private Integer ocrDeviceId;
    @JsonProperty("containerId") private Long containerId;
    @JsonProperty("createdTime") private LocalDateTime createdTime;
}
