package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TopRow {
    private int globalCode;
    private String titleZh;
    private String titleEn;
    private long count;          // or totalDurationSeconds
    private long totalDurationSeconds;
}
