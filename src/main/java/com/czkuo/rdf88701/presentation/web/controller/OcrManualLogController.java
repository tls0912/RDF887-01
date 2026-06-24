package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.report.ocr.OcrManualLogRow;
import com.czkuo.rdf88701.application.service.ocr.OcrManualLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrManualLogController {

    private final OcrManualLogService service;
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    @GetMapping("/spans")
    public List<OcrManualLogRow> spans(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to) {

        return service.spans(from, to);
    }

    // ---------- 6) 最簡單的健康檢查 ----------
    @GetMapping("/ping")
    public Object ping() {
        return Collections.singletonMap("ok", true);
    }
}
