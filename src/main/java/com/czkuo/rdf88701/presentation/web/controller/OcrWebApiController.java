package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.ocr.OcrEventService;
import com.czkuo.rdf88701.domain.dto.ocr.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * OCR → 迅得 回呼入口（僅驗證/記錄/委派到 Service）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class OcrWebApiController {

    private static final int MAX_LOG_LEN = 8000;

    private final OcrEventService ocrEventService;
    private final ObjectMapper objectMapper;

    /** 2) 任務開始通知（OCR → 迅得） */
    @PostMapping(value = "/ocr-task-started", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> taskStarted(@RequestBody @Valid OcrTaskStartedBody body,
                                            @RequestHeader(value = "X-Request-Id", required = false) String reqId) {
        final long t0 = System.currentTimeMillis();
        log.info("[OCR] TaskStarted 收到：reqId={}, body={}", clip(reqId), clip(json(body)));
        ocrEventService.onTaskStarted(body);
        log.info("[OCR] TaskStarted OK：reqId={}, tookMs={}", clip(reqId), (System.currentTimeMillis() - t0));
        return ResponseEntity.ok().build();
    }

    /** 3) 任務完成通知（OCR → 迅得） */
    @PostMapping(value = "/ocr-task-completed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> taskCompleted(@RequestBody @Valid OcrTaskCompletedBody body,
                                              @RequestHeader(value = "X-Request-Id", required = false) String reqId) {
        final long t0 = System.currentTimeMillis();
        log.info("[OCR] TaskCompleted 收到：reqId={}, body={}", clip(reqId), clip(json(body)));
        ocrEventService.onTaskCompleted(body);
        log.info("[OCR] TaskCompleted OK：reqId={}, tookMs={}", clip(reqId), (System.currentTimeMillis() - t0));
        return ResponseEntity.ok().build();
    }

    /** 7) 設備狀態變更通知（OCR → 迅得） */
    @PostMapping(value = "/ocr-device-status-changed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> deviceStatusChanged(@RequestBody @Valid OcrDeviceStatusChangedBody body,
                                                    @RequestHeader(value = "X-Request-Id", required = false) String reqId) {
        final long t0 = System.currentTimeMillis();
        log.info("[OCR] DeviceStatusChanged 收到：reqId={}, body={}", clip(reqId), clip(json(body)));
        ocrEventService.onDeviceStatusChanged(body);
        log.info("[OCR] DeviceStatusChanged OK：reqId={}, tookMs={}", clip(reqId), (System.currentTimeMillis() - t0));
        return ResponseEntity.ok().build();
    }

    /** 8) 警報發生通知（OCR → 迅得） */
    @PostMapping(value = "/ocr-alarm-raised", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> alarmRaised(@RequestBody @Valid OcrAlarmRaisedBody body,
                                            @RequestHeader(value = "X-Request-Id", required = false) String reqId) {
        final long t0 = System.currentTimeMillis();
        log.info("[OCR] AlarmRaised 收到：reqId={}, body={}", clip(reqId), clip(json(body)));
        ocrEventService.onAlarmRaised(body);
        log.info("[OCR] AlarmRaised OK：reqId={}, tookMs={}", clip(reqId), (System.currentTimeMillis() - t0));
        return ResponseEntity.ok().build();
    }

    /** 9) 警報解除通知（OCR → 迅得） */
    @PostMapping(value = "/ocr-alarm-cleared", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> alarmCleared(@RequestBody @Valid OcrAlarmClearedBody body,
                                             @RequestHeader(value = "X-Request-Id", required = false) String reqId) {
        final long t0 = System.currentTimeMillis();
        log.info("[OCR] AlarmCleared 收到：reqId={}, body={}", clip(reqId), clip(json(body)));
        ocrEventService.onAlarmCleared(body);
        log.info("[OCR] AlarmCleared OK：reqId={}, tookMs={}", clip(reqId), (System.currentTimeMillis() - t0));
        return ResponseEntity.ok().build();
    }

    // ---------------- Helpers ----------------

    private String json(Object obj) {
        if (obj == null) return "null";
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return String.valueOf(obj); }
    }

    private String clip(String s) {
        if (s == null) return "null";
        if (s.length() <= MAX_LOG_LEN) return s;
        return s.substring(0, MAX_LOG_LEN) + "...(truncated," + s.length() + " chars)";
    }
}
