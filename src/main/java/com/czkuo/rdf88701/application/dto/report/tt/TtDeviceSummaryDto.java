package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.math.BigDecimal;

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
