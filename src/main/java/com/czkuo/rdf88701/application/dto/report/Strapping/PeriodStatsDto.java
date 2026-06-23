package com.czkuo.rdf88701.application.dto.report.Strapping;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PeriodStatsDto {
    private String periodLabel;          // e.g. "2025-10-01" / "2025-W40" / "2025-10"
    private StrappingStatsResult stats;  // 該期間統計
    private List<MachineStatsDto> machineStats = new ArrayList<>();  // 依機台統計
}