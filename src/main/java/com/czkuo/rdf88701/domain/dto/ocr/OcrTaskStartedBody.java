package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 任務開始通知的請求體 */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
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
