package com.czkuo.rdf88701.application.dto.report.tt;

import lombok.Data;

import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class TtQueryFilterDto {

    private LocalDateTime from;
    private LocalDateTime to;

    private String deviceType;
    private String deviceName;
    private String remarkId;

    private Integer transferNo;
}
