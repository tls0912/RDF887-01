package com.czkuo.rdf88701.domain.dto.ocr;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 設備警報項目 */
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrAlarmItem {
    @JsonProperty("alarmCode")    private String alarmCode;
    @JsonProperty("message")      private String message;
    @JsonProperty("occurredTime") private LocalDateTime occurredTime;
}
