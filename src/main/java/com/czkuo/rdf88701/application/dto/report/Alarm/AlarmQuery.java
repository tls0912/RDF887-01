package com.czkuo.rdf88701.application.dto.report.Alarm;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
public class AlarmQuery {
    private LocalDateTime from;
    private LocalDateTime to;
    private String type;                // "ALARM" / "WARNING" / "ALL"
    private List<String> equipments;    // ["WIP","ZIPA",...]
    private String bucket;              // "day" / "hour"
}
