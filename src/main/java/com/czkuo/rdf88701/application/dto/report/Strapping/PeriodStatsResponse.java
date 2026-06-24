package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class PeriodStatsResponse {
    private String granularity;           // "day" / "week" / "month"
    private List<PeriodStatsDto> periods; // 各區間統計
    private Double averagePassRateValue;  // 區間的平均通關率（小數第二位）
    private String averagePassRate;       // 平均通關率百分比字串 (例如 "75.35%")
}
