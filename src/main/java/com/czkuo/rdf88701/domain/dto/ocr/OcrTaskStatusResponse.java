package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 查任務狀態：Response */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskStatusResponse {

    @JsonProperty("taskId")
    private Long taskId;

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("status") // e.g. QUEUED/RUNNING/SUCCESS/FAILED
    private String status;

    @JsonProperty("updatedTime") // （供向後相容；供應商實際不一定回這個）
    private LocalDateTime updatedTime;

    // ---------- 依狀態回傳的可選欄位（對齊你的規格） ----------

    // QUEUED：回 queuedAt
    @JsonProperty("queuedAt")
    private LocalDateTime queuedAt;

    // RUNNING：回 startTime
    @JsonProperty("startTime")
    private LocalDateTime startTime;

    // SUCCESS/FAILED：都會回 containerId + completedTime
    @JsonProperty("containerId")
    private Long containerId;

    @JsonProperty("completedTime")
    private LocalDateTime completedTime;

    // SUCCESS 專用：ocrText + timingBreakdown
    @JsonProperty("ocrText1")
    private String ocrText1;

    @JsonProperty("ocrText2")
    private String ocrText2;

    @JsonProperty("timingBreakdown")
    private TimingBreakdown timingBreakdown;

    // FAILED 專用：errorMessage
    @JsonProperty("errorMessage")
    private String errorMessage;

    /** 時間分解（毫秒）- 與 OcrTaskCompletedBody.TimingBreakdown 對齊 */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TimingBreakdown {
        @JsonProperty("captureTime")
        private Integer captureTime;

        @JsonProperty("ocrProcessing")
        private Integer ocrProcessing;

        @JsonProperty("resultPackaging")
        private Integer resultPackaging;
    }
}
