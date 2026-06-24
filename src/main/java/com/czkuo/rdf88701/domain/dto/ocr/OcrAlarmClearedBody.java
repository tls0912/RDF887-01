package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 警報解除通知（OCR → 迅得） */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrAlarmClearedBody {

    @NotNull
    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @NotNull
    @JsonProperty("alarmCode")
    private String alarmCode;

    @NotNull
    @JsonProperty("clearedTime")
    private LocalDateTime clearedTime;

    @JsonProperty("message")
    private String message; // 選填：補充說明
}
