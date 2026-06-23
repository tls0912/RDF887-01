package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.report.Alarm.AlarmQuery;
import com.czkuo.rdf88701.application.dto.report.Alarm.TimelineBucketRow;
import com.czkuo.rdf88701.application.dto.report.Alarm.TopRow;
import com.czkuo.rdf88701.application.dto.report.Alarm.TriggerSpanRow;
import com.czkuo.rdf88701.application.service.report.AlarmReportHybridService;
import com.czkuo.rdf88701.application.service.report.AlarmReportHybridService.AlarmOverview;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/reports/alarms")
@RequiredArgsConstructor
public class AlarmReportController {

    private final AlarmReportHybridService service;
    private static final ZoneId ZONE = ZoneId.of("Asia/Taipei");

    // ---------- Helpers ----------
    private static List<String> normEquipments(List<String> eqs) {
        if (eqs == null || eqs.isEmpty()) return null;
        // 支援單參數用逗號分隔的情況：?equipments=ZIPA,ZIPB
        if (eqs.size() == 1 && StringUtils.hasText(eqs.get(0)) && eqs.get(0).contains(",")) {
            return Arrays.stream(eqs.get(0).split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        return eqs;
    }

    private static String normType(String t) {
        if (!StringUtils.hasText(t)) return "ALL";
        t = t.toUpperCase();
        return (t.equals("ALARM") || t.equals("WARNING")) ? t : "ALL";
    }

    // ---------- 1) Timeline ----------
    @GetMapping("/timeline")
    public List<TimelineBucketRow> timeline(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "hour") String bucket,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> equipments) {

        AlarmQuery q = new AlarmQuery();
        q.setFrom(from);
        q.setTo(to);
        q.setBucket("day".equalsIgnoreCase(bucket) ? "day" : "hour");
        q.setType(normType(type));
        q.setEquipments(normEquipments(equipments));
        return service.timeline(q);
    }

    // ---------- 2) Top（count / duration） ----------
    @GetMapping("/top")
    public List<TopRow> top(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> equipments,
            @RequestParam(defaultValue = "count") String metric,
            @RequestParam(defaultValue = "20") int limit) {

        String m = ("duration".equalsIgnoreCase(metric)) ? "duration" : "count";
        int lim = Math.max(1, Math.min(limit, 1000));
        return service.top(from, to, normType(type), normEquipments(equipments), m, lim);
    }

    // ---------- 3) Spans 明細 ----------
    @GetMapping("/spans")
    public List<TriggerSpanRow> spans(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> equipments,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        int pg = Math.max(0, page);
        int sz = Math.max(1, Math.min(size, 2000));
        return service.spans(from, to, normType(type), normEquipments(equipments), pg, sz);
    }

    // ---------- 4) 未清清單（Open） ----------
    @GetMapping("/open")
    public List<TriggerSpanRow> open(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> equipments) {

        LocalDateTime end = (to != null) ? to : LocalDateTime.now(ZONE);
        return service.open(from, end, normType(type), normEquipments(equipments));
    }

    // ---------- 5) KPI 概覽 ----------
    @GetMapping("/overview")
    public AlarmOverview overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(required = false) List<String> equipments) {

        // 預設：from=今天 00:00，to=現在（Asia/Taipei）
        LocalDateTime f = (from != null) ? from : LocalDate.now(ZONE).atStartOfDay();
        LocalDateTime t = (to != null) ? to : LocalDateTime.now(ZONE);
        return service.overview(f, t, normType(type), normEquipments(equipments));
    }

    // ---------- 6) 最簡單的健康檢查 ----------
    @GetMapping("/ping")
    public Object ping() {
        return Collections.singletonMap("ok", true);
    }
}
