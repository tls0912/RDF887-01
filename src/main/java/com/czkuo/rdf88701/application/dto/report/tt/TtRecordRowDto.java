package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TtRecordRowDto {

    private Long id = 0L;

    private LocalDateTime createdTime;

    private String ttIndex = "";

    private String plcGroup = "";

    private Integer transferNo = 0;

    /**
     * 一筆 record 的 cycle time = sum(items.time_sec)
     */
    private BigDecimal cycleSec;
    private String remarkId = "";
    private String deviceArea = "";
    private BigDecimal timeSec;

}
