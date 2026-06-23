package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 任務開始通知的請求體 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskStartedBody {

    @JsonProperty("taskId")
    private Long taskId;

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("startTime")
    private LocalDateTime startTime;
}
