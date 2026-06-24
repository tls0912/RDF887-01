package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.report.Alarm.AlarmActionLogRow;
import com.czkuo.rdf88701.application.service.report.AlarmActionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/Alarm")
@RequiredArgsConstructor
public class AlarmActionLogController {

    private final AlarmActionLogService service;
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    @GetMapping("/spans")
    public List<AlarmActionLogRow> spans(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {

        return service.spans(from, to);
    }
    @GetMapping("/spansGroup")
    public List<AlarmActionLogRow> spansGroup(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {

        return service.spansGroup(from, to);
    }
    @PostMapping("/import")
    public Object importLogs(
            @RequestBody List<AlarmActionLogRow> rows) {
        Integer count = service.importLogs(rows);
        return Collections.singletonMap(
                "importedCount", count
        );

    }

    // ---------- 6) 最簡單的健康檢查 ----------
    @GetMapping("/ping")
    public Object ping() {
        return Collections.singletonMap("ok", true);
    }
}
