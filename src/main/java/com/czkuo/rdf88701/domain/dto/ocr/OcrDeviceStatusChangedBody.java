package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 設備狀態變更通知 */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrDeviceStatusChangedBody {

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("status") // IDLE/BUSY/ERROR/MAINTENANCE/OFFLINE
    private String status;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}
