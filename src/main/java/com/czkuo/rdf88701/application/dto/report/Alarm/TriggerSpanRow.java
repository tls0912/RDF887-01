package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TriggerSpanRow {
    private int globalCode;
    private String itemType;
    private String equipment;
    private LocalDateTime triggerTime;
    private LocalDateTime clearTime;
    private long durationSeconds;
}
