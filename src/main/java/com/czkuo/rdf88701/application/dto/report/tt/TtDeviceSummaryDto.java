package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.math.BigDecimal;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TtDeviceSummaryDto {

    private String deviceType;
    private String deviceName;

    /** record 筆數 */
    private int recordCount;

    /** cycle time 平均（秒） */
    private BigDecimal avgCycleSec;

    /** cycle time P95（秒） */
    private BigDecimal p95CycleSec;
}
