package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.dto.report.Strapping.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * StrappingStatsService
 * ---------------------------------------------------
 * 以「每台機台為獨立時間軸」統計打帶紀錄的成功/失敗/異常。
 * <p>
 * 定義
 * - Bundle：同一個 product_id 在同一台打帶機上的一段連續處理。
 * - Shot  ：一次打帶結果（OK/NG）。
 * <p>
 * 規則
 * 1) 先依 machine_pos 分組，逐台掃描（其他機台的事件不會打斷本台的 bundle）。
 * 2) 同台內遇到 product_id 改變 → 結束前一把、開新把。
 * 3) 各台理論 OK 次數 REQUIRED_OK：
 * #1 -> 2、#2 -> 2、#3 -> 1。
 * 4) 判定：
 * - 成功：OK == required（記錄 successDetails，重置）
 * - 失敗：bundle 結束時 OK < required（記錄 failDetails，重置）※ 不列入異常
 * - 異常：OK > required（記錄 abnormalDetails，重置）
 * <p>
 * 統計
 * - successBundles / successShots
 * - failBundles / failShots（包含成功把中的 NG，以便觀察 NG 量）
 * - abnormalBundles
 * - successDetails / failDetails / abnormalDetails
 * <p>
 * 備註
 * - 若要計算通關率（不含異常）：passRate = successBundles / (successBundles + failBundles)
 */
@Service
public class StrappingStatsService {

    /**
     * 各台機台理論需要的 OK 次數
     */
    private static final Map<Integer, Integer> REQUIRED_OK = Map.of(
            1, 2,  // 機台#1：OK×2
            2, 2,  // 機台#2：OK×2
            3, 1   // 機台#3：OK×1
    );

    /**
     * 對一段時間內的所有紀錄做總體統計（服務內先按機台分組，再逐台計算）
     */
    public StrappingStatsResult analyze(List<StrappingLogRow> allRows) {
        allRows.sort(Comparator.comparing(StrappingLogRow::getEventTime));

        StrappingStatsResult total = new StrappingStatsResult();
        total.setSuccessDetails(new ArrayList<>());
        total.setFailDetails(new ArrayList<>());
        total.setAbnormalDetails(new ArrayList<>());

        // 依機台分組
        Map<Integer, List<StrappingLogRow>> byMachine =
                allRows.stream().collect(Collectors.groupingBy(
                        StrappingLogRow::getMachinePos,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 逐台計算 + 彙總
        for (Map.Entry<Integer, List<StrappingLogRow>> e : byMachine.entrySet()) {
            int machine = e.getKey();
            List<StrappingLogRow> rows = e.getValue();
            rows.sort(Comparator.comparing(StrappingLogRow::getEventTime));

            StrappingStatsResult per = analyzeOneMachine(machine, rows);

            total.setSuccessBundles(total.getSuccessBundles() + per.getSuccessBundles());
            total.setSuccessShots(total.getSuccessShots() + per.getSuccessShots());
            total.setFailBundles(total.getFailBundles() + per.getFailBundles());
            total.setFailShots(total.getFailShots() + per.getFailShots());
            total.setAbnormalBundles(total.getAbnormalBundles() + per.getAbnormalBundles());

            total.getSuccessDetails().addAll(per.getSuccessDetails());
            total.getFailDetails().addAll(per.getFailDetails());
            total.getAbnormalDetails().addAll(per.getAbnormalDetails());
        }

        // 計算通關率
        int denom = total.getSuccessBundles() + total.getFailBundles();
        if (denom == 0) {
            total.setPassRateValue(0.0);
            total.setPassRate("0.00%");
        } else {
            double rate = (double) total.getSuccessBundles() / denom;
            total.setPassRateValue(rate);
            total.setPassRate(String.format("%.2f%%", rate * 100));
        }

        return total;
    }

    /**
     * 單台機台的統計核心：只處理同一個 machine_pos 的序列。
     */
    private StrappingStatsResult analyzeOneMachine(int machine, List<StrappingLogRow> rows) {
        StrappingStatsResult r = new StrappingStatsResult();
        r.setSuccessDetails(new ArrayList<>());
        r.setFailDetails(new ArrayList<>());
        r.setAbnormalDetails(new ArrayList<>());

        String currentPid = null;
        LocalDateTime start = null;
        int okCount = 0;
        int ngCount = 0;
        int abnormalCount = 0;
        StrappingLogRow prev = null;
        final int required = REQUIRED_OK.getOrDefault(machine, 1);

        for (StrappingLogRow row : rows) {
            boolean newBundle = !Objects.equals(currentPid, row.getProductId());

            // 切到新 product → 結束上一把
            if (newBundle && currentPid != null) {
                LocalDateTime endTime = (prev != null ? prev.getEventTime() : row.getEventTime());
                finishBundle(r, currentPid, machine, start, endTime, okCount, ngCount, required, abnormalCount);
                currentPid = null;
                start = null;
                okCount = 0;
                ngCount = 0;
                abnormalCount = 0;
            }

            // 初始化
            if (currentPid == null) {
                currentPid = row.getProductId();
                start = row.getEventTime();
            }

            // 累積 OK/NG
            if ("OK".equalsIgnoreCase(row.getResult())) okCount++;
            else if ("NG".equalsIgnoreCase(row.getResult())) ngCount++;
            else
                abnormalCount++;

            // 成功：剛好等於 required
            if (okCount == required) {
                if (currentPid == null || currentPid.isBlank()) {
                    // productId 空 → 異常，不算成功
                    addAbnormal(r, currentPid, machine, start, row.getEventTime(),
                            required, okCount, ngCount, "空的 ProductId");
                } else {
                    // 正常成功
                    r.setSuccessBundles(r.getSuccessBundles() + 1);
                    r.setSuccessShots(r.getSuccessShots() + okCount);
                    if (ngCount > 0) r.setFailShots(r.getFailShots() + ngCount);

                    var s = new StrappingSuccessRecord();
                    s.setProductId(currentPid);
                    s.setMachinePos(machine);
                    s.setStartTime(start);
                    s.setEndTime(row.getEventTime());
                    s.setExpectedOk(required);
                    s.setActualOk(okCount);
                    s.setNgCount(ngCount);
                    r.getSuccessDetails().add(s);
                }

                currentPid = null;
                start = null;
                okCount = 0;
                ngCount = 0;
            }
            // 異常：OK 超過 required
            else if (okCount > required) {
                int extraOk = okCount - required; // 超標 OK 數量
                addAbnormal(r, currentPid, machine, start, row.getEventTime(),
                        required, okCount, ngCount, "OK 次數超過理論值 (+ " + extraOk + ")");
                currentPid = null;
                start = null;
                okCount = 0;
                ngCount = 0;
            }

            prev = row;
        }

        // 收尾
        if (currentPid != null && (okCount > 0 || ngCount > 0 || abnormalCount > 0)) {
            LocalDateTime endTime = (prev != null ? prev.getEventTime() : start);
            finishBundle(r, currentPid, machine, start, endTime, okCount, ngCount, required, abnormalCount);
        }

        return r;
    }

    /**
     * 結算一把（成功或失敗）。※這裡的「失敗」不算異常，只是 OK 未達標。
     */
    private void finishBundle(
            StrappingStatsResult r,
            String pid, int machine, LocalDateTime start, LocalDateTime end,
            int okCount, int ngCount, int required, int abnormalCount) {
        if (okCount == required) {
            // 成功
            r.setSuccessBundles(r.getSuccessBundles() + 1);
            r.setSuccessShots(r.getSuccessShots() + okCount);
            r.setFailShots(r.getFailShots() + ngCount);
            r.setAbnormalBundles(r.getAbnormalBundles() + abnormalCount);

            var s = new StrappingSuccessRecord();
            s.setProductId(pid);
            s.setMachinePos(machine);
            s.setStartTime(start);
            s.setEndTime(end);
            s.setExpectedOk(required);
            s.setActualOk(okCount);
            s.setNgCount(ngCount);
            r.getSuccessDetails().add(s);

        } else {
            // 失敗（OK 未達 required）→ 記到 failDetails，不算 abnormal
            r.setFailBundles(r.getFailBundles() + 1);
            r.setSuccessShots(r.getSuccessShots() + okCount);
            r.setFailShots(r.getFailShots() + ngCount);
            r.setAbnormalBundles(r.getAbnormalBundles() + abnormalCount);

            var f = new StrappingFailRecord();
            f.setProductId(pid);
            f.setMachinePos(machine);
            f.setStartTime(start);
            f.setEndTime(end);
            f.setExpectedOk(required);
            f.setActualOk(okCount);
            f.setNgCount(ngCount);
            f.setReason("不足 " + required + " 次 OK");
            r.getFailDetails().add(f);
        }
    }

    /**
     * 記錄異常（例如 OK > required）。異常不參與通關率計算。
     */
    private void addAbnormal(
            StrappingStatsResult r,
            String pid, int machine, LocalDateTime start, LocalDateTime end,
            int required, int actualOk, int ngCount, String reason) {

        var ab = new StrappingAbnormalRecord();
        ab.setProductId(pid);
        ab.setMachinePos(machine);
        ab.setStartTime(start);
        ab.setEndTime(end);
        ab.setExpectedOk(required);
        ab.setActualOk(actualOk);
        ab.setNgCount(ngCount);
        ab.setReason(reason);

        r.setAbnormalBundles(r.getAbnormalBundles() + 1);

        // 計算異常次數
        int abnormalShots = 0;
        if (pid == null || pid.isBlank()) {
            abnormalShots = actualOk + ngCount; // 空容器 → 當下所有 shot 都算異常
        } else if (actualOk > required) {
            abnormalShots = actualOk - required; // 超標的部分算異常 shot
        }
        r.setAbnormalShots(r.getAbnormalShots() + abnormalShots);

        r.getAbnormalDetails().add(ab);
    }
}
