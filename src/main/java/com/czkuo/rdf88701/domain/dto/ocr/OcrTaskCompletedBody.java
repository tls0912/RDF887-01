package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 任務完成通知的請求體
 * status 允許：SUCCESS / FAILED
 * - SUCCESS: 可帶 ocrText、timingBreakdown
 * - FAILED : 可帶 errorMessage
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrTaskCompletedBody {

    @JsonProperty("taskId")
    private Long taskId;

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("containerId")
    private Long containerId;

    @JsonProperty("status") // SUCCESS / FAILED
    private String status;

    @JsonProperty("completedTime")
    private java.time.LocalDateTime completedTime;

    @JsonProperty("ocrText1")
    private String ocrText1;

    @JsonProperty("ocrText2")
    private String ocrText2;

    @JsonProperty("timingBreakdown")
    private TimingBreakdown timingBreakdown;

    @JsonProperty("errorMessage")
    private String errorMessage;

    /** 時間分解（毫秒） */
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
