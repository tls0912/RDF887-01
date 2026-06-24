package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TimelineBucketRow {
    private String bucketStart;  // "2025-10-17 00:00:00"
    private long triggerCount;
    private long clearCount;
}
