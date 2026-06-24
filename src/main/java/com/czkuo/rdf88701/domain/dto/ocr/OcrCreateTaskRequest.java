package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 建立任務：Request */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrCreateTaskRequest {
    @JsonProperty("taskId")      private Long taskId;
    @JsonProperty("ocrDeviceId") private Integer ocrDeviceId;
    @JsonProperty("containerId") private Long containerId;
    @JsonProperty("createdTime") private LocalDateTime createdTime;
}
