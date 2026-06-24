package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.dto.report.Strapping.*;
import com.czkuo.rdf88701.application.service.StrappingStatsService;
import com.czkuo.rdf88701.domain.repository.StrappingLogRepository;
import com.czkuo.rdf88701.infra.entity.StrappingLog;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@RestController
@RequestMapping("/api/strapping")
@RequiredArgsConstructor
public class StrappingReportController {

    private final StrappingLogRepository repo;
    private final StrappingStatsService statsService;

    // ========== 單次統計 ==========

    /**
     * 查詢指定時間區間內的打帶統計
     * @param start      開始時間 (yyyy-MM-dd HH:mm:ss)
     * @param end        結束時間 (yyyy-MM-dd HH:mm:ss)
     * @param machinePos (可選) 指定機台 (1/2/3)，不填代表全部
     */
    @GetMapping("/stats")
    public StrappingStatsResult getStats(
            @RequestParam("start")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam("end")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @RequestParam(value = "machinePos", required = false) Byte machinePos
    ) {
        List<StrappingLog> logs = (machinePos != null)
                ? repo.findByTimeRangeAndMachine(start, end, machinePos)
                : repo.findByTimeRange(start, end);

        // 映射成統計用 Row DTO
        List<StrappingLogRow> rows = logs.stream()
                .map(this::toRow)
                .collect(Collectors.toList());
        return statsService.analyze(rows);
    }

    // ========== 區間多粒度統計 ==========

    /**
     * 依指定時間區間與粒度（day / week / month）統計
     * @param start       開始日期 (yyyy-MM-dd)
     * @param end         結束日期 (yyyy-MM-dd)
     * @param granularity 粒度：day / week / month
     * @param machinePos  (可選) 指定機台
     */
    @GetMapping("/stats/range")
    public PeriodStatsResponse getRangeStats(
            @RequestParam("start") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam("end") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
            @RequestParam("granularity") String granularity,
            @RequestParam(value = "machinePos", required = false) Byte machinePos
    ) {
        List<PeriodStatsDto> results = new ArrayList<>();
        LocalDate cursor = start;

        switch (granularity.toLowerCase()) {
            case "day" -> {
                while (!cursor.isAfter(end)) {
                    LocalDateTime s = cursor.atStartOfDay();
                    LocalDateTime e = cursor.atTime(LocalTime.MAX);
                    results.add(analyzePeriod(cursor.toString(), s, e, machinePos));
                    cursor = cursor.plusDays(1);
                }
            }
            case "week" -> {
                while (!cursor.isAfter(end)) {
                    LocalDate startOfWeek = cursor.with(DayOfWeek.MONDAY);
                    LocalDate endOfWeek = cursor.with(DayOfWeek.SUNDAY);

                    LocalDateTime s = startOfWeek.atStartOfDay();
                    LocalDateTime e = endOfWeek.atTime(LocalTime.MAX);

                    if (s.toLocalDate().isBefore(start)) s = start.atStartOfDay();
                    if (e.toLocalDate().isAfter(end)) e = end.atTime(LocalTime.MAX);

                    String label = startOfWeek + " ~ " + endOfWeek;
                    results.add(analyzePeriod(label, s, e, machinePos));

                    cursor = cursor.plusWeeks(1);
                }
            }
            case "month" -> {
                while (!cursor.isAfter(end)) {
                    LocalDate startOfMonth = cursor.withDayOfMonth(1);
                    LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());

                    LocalDateTime s = startOfMonth.atStartOfDay();
                    LocalDateTime e = endOfMonth.atTime(LocalTime.MAX);

                    if (s.toLocalDate().isBefore(start)) s = start.atStartOfDay();
                    if (e.toLocalDate().isAfter(end)) e = end.atTime(LocalTime.MAX);

                    String label = startOfMonth.getYear() + "-" + String.format("%02d", startOfMonth.getMonthValue());
                    results.add(analyzePeriod(label, s, e, machinePos));

                    cursor = cursor.plusMonths(1);
                }
            }
            default -> throw new IllegalArgumentException("granularity must be day/week/month");
        }

        PeriodStatsResponse resp = new PeriodStatsResponse();
        resp.setGranularity(granularity.toLowerCase());
        resp.setPeriods(results);

        // ===== 平均通關率 =====
        double avg = results.stream()
                .mapToDouble(p -> p.getStats().getPassRateValue())
                .average()
                .orElse(0.0);

        // 四捨五入到小數第二位
        double rounded = Math.round(avg * 100.0) / 100.0;
        resp.setAveragePassRateValue(rounded);
        resp.setAveragePassRate(String.format("%.2f%%", rounded * 100));
        return resp;
    }

    /**
     * 查詢指定 productId 在某段時間 / 機台的詳細打帶紀錄
     * - 可用來追蹤異常或失敗把的實際打帶過程
     *
     * @param productId  必填，產品編號
     * @param machinePos 必填，指定機台
     * @param start      (可選) 開始時間
     * @param end        (可選) 結束時間
     */
    @GetMapping("/details")
    public List<StrappingLogRow> getDetails(
            @RequestParam("productId") String productId,
            @RequestParam("machinePos") Byte machinePos,
            @RequestParam(value = "start", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @RequestParam(value = "end", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end
    ) {
        // 預設查詢範圍：最近 30 天
        LocalDateTime s = (start != null) ? start : LocalDateTime.now().minusDays(30);
        LocalDateTime e = (end != null) ? end : LocalDateTime.now();

        List<StrappingLog> logs = repo.findByTimeRangeAndMachine(s, e, machinePos).stream()
                .filter(l -> productId.equals(l.getProductId()))
                .collect(Collectors.toList());

        return logs.stream()
                .map(this::toRow)
                .collect(Collectors.toList());
    }


    // ========== helper methods ==========

    private StrappingLogRow toRow(StrappingLog l) {
        StrappingLogRow row = new StrappingLogRow();
        row.setProductId(l.getProductId());
        row.setMachinePos(l.getMachinePos());
        row.setResult(l.getResult() == 1 ? "OK" :l.getResult() == 2 ? "NG": "ERROR");
        row.setEventTime(l.getEventTime());
        return row;
    }

    private PeriodStatsDto analyzePeriod(String label, LocalDateTime start, LocalDateTime end, Byte machinePos) {
        List<StrappingLog> logs = (machinePos != null)
                ? repo.findByTimeRangeAndMachine(start, end, machinePos)
                : repo.findByTimeRange(start, end);

        List<StrappingLogRow> rows = logs.stream()
                .map(this::toRow)
                .collect(Collectors.toList());
        StrappingStatsResult stats = statsService.analyze(rows);

        List<MachineStatsDto> machines = new ArrayList<>();
        // 固定列出 1~3 台；即使沒有資料也顯示 0（UI 會好看）
        for (int m = 1; m <= 3; m++) {
            int machine = m;
            List<StrappingLogRow> sub = rows.stream()
                    .filter(r -> r.getMachinePos() == machine)
                    .collect(Collectors.toList());

            StrappingStatsResult ms = statsService.analyze(sub); // 傳入單一機台資料也 OK

            MachineStatsDto dto = new MachineStatsDto();
            dto.setMachinePos((byte) machine);
            dto.setMachineName("STRAP#" + machine);
            dto.setSuccessBundles(ms.getSuccessBundles());
            dto.setSuccessShots(ms.getSuccessShots());
            dto.setFailShots(ms.getFailShots());
            dto.setAbnormalBundles(ms.getAbnormalBundles());
            dto.setPassRateValue(ms.getPassRateValue());
            dto.setPassRate(ms.getPassRate());

            machines.add(dto);
        }

        PeriodStatsDto dto = new PeriodStatsDto();
        dto.setPeriodLabel(label);
        dto.setStats(stats);
        dto.setMachineStats(machines);
        return dto;
    }
}
