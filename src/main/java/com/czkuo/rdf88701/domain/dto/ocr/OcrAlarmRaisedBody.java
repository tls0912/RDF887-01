package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 警報推送通知 */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrAlarmRaisedBody {

    @JsonProperty("ocrDeviceId")
    private Integer ocrDeviceId;

    @JsonProperty("alarmCode")
    private String alarmCode;

    @JsonProperty("message")
    private String message;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}
