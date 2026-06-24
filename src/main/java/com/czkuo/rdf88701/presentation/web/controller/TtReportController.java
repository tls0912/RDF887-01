package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.report.tt.*;
import com.czkuo.rdf88701.application.service.tt.TtReportService;
import com.czkuo.rdf88701.common.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.mqttv5.client.internal.ClientState;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tt")
public class TtReportController {

    private final TtReportService svc;

    @GetMapping("/summary")
    public List<TtDeviceSummaryDto> summary(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer transferNo,
            @RequestParam(required = false) String remarkId
    ) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(from);
        f.setTo(to);
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        f.setTransferNo(transferNo);
        f.setRemarkId(remarkId);
        return svc.getSummary(f);
    }

    @GetMapping("/records")
    public PageResult<TtRecordRowDto> records(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer transferNo,
            @RequestParam(required = false) String remarkId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(from);
        f.setTo(to);
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        f.setTransferNo(transferNo);
        f.setRemarkId(remarkId);
        return svc.getRecordsPage(f, pageNum, pageSize);
    }

    @GetMapping("/records/{recordId}/items")
    public List<TtRecordItemRowDto> items(@PathVariable long recordId) {
        return svc.getItems(recordId);
    }

    @GetMapping("/summaryGroupID")
    public List<TtRecordRowGroupIdDto> summaryGroupID(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer transferNo,
            @RequestParam(required = false) String remarkId
    ) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(from);
        f.setTo(to);
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        f.setTransferNo(transferNo);
        f.setRemarkId(remarkId);
        return svc.getSummaryGroupID(f);
    }
    @GetMapping("/recordsGroupID")
    public PageResult<TtRecordRowDto> recordsGroupID(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer transferNo,
            @RequestParam(required = false) String remarkId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(from);
        f.setTo(to);
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        f.setTransferNo(transferNo);
        f.setRemarkId(remarkId);
        return svc.getRecordsPageGroupId(f, pageNum, pageSize);
    }
    @GetMapping("/exportData")
    public List<TtRecordRowDto> exportData(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) Integer transferNo,
            @RequestParam(required = false) String remarkId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        TtQueryFilterDto f = new TtQueryFilterDto();
        f.setFrom(from);
        f.setTo(to);
        f.setDeviceType(deviceType);
        f.setDeviceName(deviceName);
        f.setTransferNo(transferNo);
        f.setRemarkId(remarkId);
        return svc.getExportData(f);
    }
}
