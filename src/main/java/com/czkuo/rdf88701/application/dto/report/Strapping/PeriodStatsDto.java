package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class PeriodStatsDto {
    private String periodLabel;          // e.g. "2025-10-01" / "2025-W40" / "2025-10"
    private StrappingStatsResult stats;  // 該期間統計
    private List<MachineStatsDto> machineStats = new ArrayList<>();  // 依機台統計
}