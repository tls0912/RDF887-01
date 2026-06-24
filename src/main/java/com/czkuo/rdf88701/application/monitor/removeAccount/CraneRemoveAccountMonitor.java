package com.czkuo.rdf88701.application.monitor.removeAccount;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcCraneBitWriter;
import com.czkuo.rdf88701.infra.cache.CraneStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.RobotR008Task;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CraneRemoveAccountMonitor
 * ------------------------------------------------------------
 * - 監看每台 Crane 的 removeAccountReq（PLC→PC / Read 區）
 * - 上緣：清掉 Crane 位置的 LocationTracking，並回 removeAccountAck=1
 * - 下緣：將 removeAccountAck=0
 * - 記錄當下的 CST ID（若能取到），並提供防禦 rearm / 強制拉低機制
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneRemoveAccountMonitor {

    private final CraneStatusCache craneStatusCache;                     // Read 區（bits/words）快照（以 craneName 存）
    private final PlcCraneRegistry plcCraneRegistry;                     // id <-> name 對照
    private final PlcCraneBitWriter plcCraneBitWriter;                   // 寫 removeAccountAck
    private final LocationPointRepository locationPointRepository;       // 位置資訊
    private final LocationFlowRepository locationFlowRepository;         // 位置紀錄
    private final LocationTrackingRepository locationTrackingRepository; // 清空位置帳務
    private final CraneTaskRepository craneTaskRepository;               // 清空帳務任務

    // 類別欄位區域（與其他 repository/服務同層）
    private final R029OutputCaptureService r029OutputCaptureService;

    // 類別欄位新增
    private final ContainerMainRepository containerMainRepository;
    private final RobotR008TaskRepository r008TaskRepository;
    private final MqttMessageEventPublisher eventPublisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    // === 週期＆掃描設定 ===
    @Value("${monitor.crane-remove-ack.poll-ms:300}")
    private long pollMs;

    @Value("${monitor.crane-remove-ack.stale-threshold-sec:3}")
    private int staleThresholdSec;

    @Value("${monitor.crane-remove-ack.scan-max-crane-id:1}")
    private int scanMaxCraneId;

    /** 例如 "1,2,3"；空字串=1..scanMaxCraneId */
    @Value("${monitor.crane-remove-ack.targets:}")
    private String targetsCsv;

    // === 防禦參數 ===
    /** req=1 且 ACK=1 維持 N 秒，視為卡住，rearm（ACK 0→1） */
    @Value("${monitor.crane-remove-ack.stuck-req-rearm-sec:10}")
    private long stuckReqRearmSec;

    /** req=0 但 ACK=1 維持 N 秒，強制拉低 ACK */
    @Value("${monitor.crane-remove-ack.ack-force-drop-sec:5}")
    private long ackForceDropSec;

    /** 每台 crane 每小時最多 rearm 次數 */
    @Value("${monitor.crane-remove-ack.max-rearm-per-hour:3}")
    private int maxRearmPerHour;

    // === 狀態記錄 ===
    private final Map<Integer, Boolean> lastReqMap = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> lastAckWritten = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ackSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> reqHighSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rearmCountMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> rearmWindowStartMs = new ConcurrentHashMap<>();
    private final Map<Integer, String> lastPlcProductId = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${monitor.crane-remove-ack.poll-ms:400}")
    public void sync() {
        try {
            Set<Integer> targets = resolveTargets();
            if (targets.isEmpty()) return;

            long now = System.currentTimeMillis();
            int clears = 0, ackWrites = 0, rearms = 0, forcedDrops = 0;

            for (Integer craneId : targets) {
                final String craneName = safeCraneName(craneId);
                if (craneName == null) continue;

                // 1) 取現場狀態（略過過期/不完整）
                CraneDeviceStatus s = craneStatusCache.getLatest(craneName);
                if (s == null || !s.isValidAndComplete(staleThresholdSec)) {
                    continue;
                }

                // 記錄 PLC 端的 Product ID（去掉尾端 NUL/控制碼，僅供稽核與 log）
                String plcProduct = normalizeAscii50(s.getProductId());
                if (plcProduct != null) lastPlcProductId.put(craneId, plcProduct);

                boolean req = s.isRemoveAccountReq();
                boolean prevReq = lastReqMap.getOrDefault(craneId, false);
                boolean ackIsHigh = lastAckWritten.getOrDefault(craneId, false);


                // 2) 上緣觸發：0→1
                if (req && !prevReq) {
                    reqHighSinceMs.put(craneId, now);

                    // (1) 先拿「清帳前」的 container 快照（清帳後就找不到了）
                    Optional<Long> cmBefore = locationTrackingRepository.findContainerAtLocationName(craneName);

                    // 目前移除時會將狀態改為 ABORTED，並寫入 closed_time。
                    cmBefore.ifPresent(containerMainId -> {
                        try {
                            boolean ok = containerMainRepository.abort(containerMainId);
                            if (ok) {
                                log.info("[CraneRemove] {} 容器狀態已更新為 ABORTED: containerMainId={}", craneName, containerMainId);
                            } else {
                                log.warn("[CraneRemove] {} 容器狀態更新失敗(可能不存在/未變更): containerMainId={}", craneName, containerMainId);
                            }
                        } catch (Exception e) {
                            log.warn("[CraneRemove] {} 容器狀態更新例外: containerMainId={}, err={}",
                                    craneName, containerMainId, e.toString(), e);
                        }
                    });

                    // (3) 先清帳（冪等，位置為真） → 新增一致性比對
                    boolean cleared = clearLocationTracking(craneName, plcProduct);
                    if (cleared) clears++;
                    else {
                        log.warn("[CraneRemove] {} 清帳失敗，仍回 ACK 以避免卡站 (product='{}')",
                                craneName, sample(plcProduct));
                    }

                    // (4) 依站點對應，嘗試取消「最新且未終結」的任務
                    cmBefore.ifPresent(containerMainId -> {
                        cancelLatestTaskForContainer(craneId, containerMainId, craneName, plcProduct);
                    });

                    // (5) 這些站點觸發 remove 時 → R008 FAIL（比照 R029：容器優先，再用 PLC 條碼） [優化]
                    tryFailR008IfAnyWithFallback(cmBefore.orElse(null), plcProduct, craneName);

                    // (6) 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029）
                    tryMarkR029Removed(cmBefore.orElse(null), plcProduct, craneName);

                    // (7) 回 ACK=1
                    if (setAckIfChanged(craneId, true)) ackWrites++;
                }

                // 3) stuck 防禦：req 長時間=1 且 ACK=1 → rearm
                if (req) {
                    long since = reqHighSinceMs.computeIfAbsent(craneId, __ -> now);
                    long sec = (now - since) / 1000;
                    if (ackIsHigh && sec >= stuckReqRearmSec && underRearmBudget(craneId, now)) {
                        log.warn("[CraneRemove] {} req 持續高電位 {}s，執行 rearm ACK (product='{}')",
                                craneName, sec, sample(lastPlcProductId.get(craneId)));
                        ackWrites += setAckIfChanged(craneId, false) ? 1 : 0;
                        ackWrites += setAckIfChanged(craneId, true) ? 1 : 0;
                        reqHighSinceMs.put(craneId, now);
                        rearms++;
                    }
                } else {
                    reqHighSinceMs.remove(craneId);
                }

                // 4) 下緣觸發：1→0（關 ACK）
                if (!req && prevReq) {
                    ackWrites += setAckIfChanged(craneId, false) ? 1 : 0;
                }

                // 5) 強制矯正：req=0 但 ACK 高電位太久 → 拉低
                if (!req && ackIsHigh) {
                    long ackSec = (now - ackSinceMs.getOrDefault(craneId, now)) / 1000;
                    if (ackSec >= ackForceDropSec) {
                        log.info("[CraneRemove] {} req=0 但 ACK 已維持 {}s，高電位過久，強制拉低",
                                craneName, ackSec);
                        ackWrites += setAckIfChanged(craneId, false) ? 1 : 0;
                        forcedDrops++;
                    }
                }

                // 6) 記錄本輪 req
                lastReqMap.put(craneId, req);
            }

            if (clears > 0 || ackWrites > 0 || rearms > 0 || forcedDrops > 0) {
                log.info("[CraneRemove] done: clears={}, ackWrites={}, rearms={}, forcedDrops={}, targets={} (pollMs={})",
                        clears, ackWrites, rearms, forcedDrops, targets.size(), pollMs);
            }
        } catch (Exception e) {
            log.warn("[CraneRemove] ❌ failure: {}", e.toString(), e);
        }
    }

    // =========================
    // helpers
    // =========================

    private static final Set<String> OPEN = Set.of("PENDING","DISPATCHED","IN_PROGRESS","RETRY");

    /**
     * 清空指定站點的 LocationTracking（冪等）
     * 1) 有 tracking → 先把 location_flow 最後一筆補離開時間與 exitType / exitOperator
     * 2) 刪除 tracking
     * 3) 將 location_point.is_occupied 設為 'N'
     *
     * 並在清帳前，比對 PLC 的 ProductId 與 DB container_code / alias_code 是否一致，
     * 不一致仍強制清除，但會寫 warning。
     */
    private boolean clearLocationTracking(String craneName, String plcProduct) {
        try {
            // 目前以 craneName 查詢對應的 LocationPoint。
            LocationPoint p = locationPointRepository.findByName(craneName)
                    .orElse(null);
            if (p == null) {
                log.warn("[CraneRemove] {} 找不到對應 LocationPoint，無法清帳", craneName);
                return false;
            }

            String reasonBase = "PLC RemoveAccountReq crane=" + craneName;
            String reason = reasonBase;

            // 先找 Tracking（找得到才有 containerMainId 可補 flow 離開 & 比對 product）
            var trackingOpt = locationTrackingRepository.findByLocationPointId(p.getId());

            if (trackingOpt.isPresent()) {
                var tr = trackingOpt.get();

                // 先比對 PLC Product vs ContainerMain（與你原本邏輯一致）
                Long cmId = tr.getContainerMainId();
                if (cmId != null) {
                    ContainerMain cm = containerMainRepository.findById(cmId).orElse(null);
                    if (cm != null) {
                        String containerCode = StringUtils.trimToEmpty(cm.getContainerCode());
                        String aliasCode = StringUtils.trimToEmpty(cm.getAliasCode());
                        boolean matches = plcProduct != null && (
                                plcProduct.equalsIgnoreCase(containerCode) ||
                                        plcProduct.equalsIgnoreCase(aliasCode)
                        );

                        if (!matches) {
                            log.warn("[CraneRemove] {} 發現容器不一致: PLC='{}' vs DB(container={}, alias={}), 強制清除",
                                    craneName, sample(plcProduct), containerCode, aliasCode);
                            reason += " (mismatch PLC=" + sample(plcProduct) + ")";
                        } else {
                            log.info("[CraneRemove] {} 容器一致 (PLC='{}')", craneName, sample(plcProduct));
                        }
                    }
                }

                // [Step 1] 將該 container 在此 location 的最後未離開紀錄標示離開
                locationFlowRepository.markExit(
                        tr.getContainerMainId(),
                        p.getId(),
                        LocalDateTime.now(),
                        ExitType.MANUAL,      // 或依實際需求改 NORMAL/PLC 等
                        "SYS-CRANE-REMOVE"    // 或你自己的 DEFAULT_OPERATOR / 登入者帳號
                );

                // [Step 2] 刪除 tracking
                locationTrackingRepository.deleteById(tr.getId());
            } else {
                log.info("[CraneRemove] {} 無容器紀錄，直接視為空位清帳", craneName);
            }

            // [Step 3] 點位改為未佔用
            p.setIsOccupied("N");
            p.setUpdatedTime(LocalDateTime.now());
            locationPointRepository.update(p);

            log.info("[CraneRemove] 清帳成功 <- {} ({})", craneName, reason);
            return true;
        } catch (Exception ex) {
            log.warn("[CraneRemove] {} 清帳例外：{}", craneName, ex.toString(), ex);
            return false;
        }
    }

    /** 取消該容器最新一筆 Crane 任務（冪等）
     *  - 僅處理未終結狀態：PENDING/DISPATCHED/IN_PROGRESS/RETRY
     *  - 更新為 CANCELLED，補 cancelledTime / doneTime
     *  - cancelledReason 附 transferId/transferName 與（若有）product 方便稽核
     *  - 查不到或已終結則不動；例外僅記錄，不拋出
     */
    private void cancelLatestTaskForContainer(int craneId,
                                              Long cmIdFromSnapshot,
                                              String craneName,
                                              String plcProduct) {
        try {
            Optional<CraneTask> opt = craneTaskRepository.findLatestByContainerMainId(cmIdFromSnapshot);
            if (opt.isEmpty()) return;

            CraneTask task = opt.get();
            String status = Optional.ofNullable(task.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus("CANCELLED");
            task.setCancelledTime(now);
            task.setDoneTime(now);
            task.setCancelledReason(
                    "Cancelled by PLC Crane RemoveAccountReq (craneId=" + craneId
                            + ", craneName=" + craneName
                            + (plcProduct == null || plcProduct.isBlank() ? "" : ", product=" + sample(plcProduct))
                            + ")"
            );
            craneTaskRepository.update(task);
            log.warn("[CraneRemove] {} 已取消容器任務 id={} (status={})",
                    craneName, task.getId(), status);

        } catch (Exception e) {
            log.warn("[CraneRemove] {} 嘗試取消任務例外：{}", craneName, e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // 取消邏輯與 R029 / R008 共通輔助
    // ------------------------------------------------------------

    /** 嘗試標記 R029 REMOVED（容器優先，再用 PLC 條碼） */
    private void tryMarkR029Removed(Long cmId, String plcProduct, String craneName) {
        try {
            if (cmId != null) {
                r029OutputCaptureService.markRemovedIfBelongs(cmId);
                log.info("[CraneRemove] {} 標記 R029: REMOVED by containerId={}", craneName, cmId);
            } else if (StringUtils.isNotBlank(plcProduct)) {
                r029OutputCaptureService.markRemovedByCarrierId(plcProduct.trim());
                log.info("[CraneRemove] {} 標記 R029: REMOVED by carrierId='{}'", craneName, sample(plcProduct));
            } else {
                log.warn("[CraneRemove] {} 想標記 R029: REMOVED 但無 container 快照且無 PLC 條碼", craneName);
            }
        } catch (Exception ex) {
            log.warn("[CraneRemove] {} 標記 R029: REMOVED 失敗：{}", craneName, ex.toString(), ex);
        }
    }

    /** 嘗試觸發 R008 FAIL（容器優先，再用 PLC 條碼） */
    private void tryFailR008IfAnyWithFallback(Long cmId, String plcProduct, String craneName) {
        Long finalCmId = cmId;
        if (finalCmId == null && StringUtils.isNotBlank(plcProduct)) {
            finalCmId = resolveContainerByAliasCode(plcProduct).orElse(null);
        }
        if (finalCmId != null) {
            tryFailR008IfAny(finalCmId, craneName, plcProduct);
        } else {
            //log.debug("[CraneRemove] {} 無法比對容器以觸發 R008 FAIL", craneName);
        }
    }

    // ------------------------------------------------------------
    // R008 FAIL（Crane#1）
    // ------------------------------------------------------------

    private void tryFailR008IfAny(Long containerMainId, String craneName, String plcProduct) {
        try {
            String carrierId = containerMainRepository.findById(containerMainId)
                    .map(ContainerMain::getAliasCode).map(String::trim).orElse(null);
            if (StringUtils.isBlank(carrierId)) {
                //log.debug("[R008][FAIL] 跳過：cmId={} 無 carrierId", containerMainId);
                return;
            }

            RobotR008Task match = r008TaskRepository.findOpen().stream()
                    .filter(t -> carrierId.equalsIgnoreCase(nz(t.getCarrierId())))
                    .findFirst().orElse(null);
            if (match == null) {
                //log.debug("[R008][FAIL] 無 open 與 carrierId={} 相符", carrierId);
                return;
            }

            R008AckPayload out = new R008AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R008");
            out.setTid(match.getTid());
            out.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");

            R008AckPayload.Message m = new R008AckPayload.Message();
            m.setLotId(match.getLotId());
            m.setCarrierId(match.getCarrierId());
            m.setWipName(nz(match.getWipName()));
            m.setDestLoc(match.getDestLoc());
            m.setEqpPort(match.getEqpPort());
            m.setTrayHigh(match.getTrayHigh());
            m.setTrayType(match.getTrayType());
            m.setBinType(match.getBinType());
            m.setTrayNum(match.getTrayNum());
            m.setDeviceName(match.getDeviceName());
            m.setMovePriority(match.getMovePriority());
            m.setMissionTrip(match.getMissionTrip());
            m.setOdo(match.getOdo());
            m.setAmrSpeed(match.getAmrSpeed());
            m.setAmrRobotSpeed(match.getAmrRobotSpeed());
            m.setPpkgBodySize(match.getPpkgBodySize());
            out.setMessage(m);

            out.setResult("FAIL");
            out.setResultMessage("Removed by " + craneName + " RemoveAccountReq"
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId("ack/r008/crane-auto-fail", logService.getLocalSystem(),
                    aseSystem, payload, MqttMessageType.ACK);

            eventPublisher.publish(aseSystem, objectMapper.writeValueAsString(out),
                    MqttMessageType.ACK, out.getTid(), out.getCmdId());

            RobotR008Task patch = new RobotR008Task();
            patch.setLogId(match.getLogId());
            patch.setExternalLastResult("FAIL");
            patch.setExternalLastTime(LocalDateTime.now());
            patch.setInternalState("FAILED");
            patch.setUpdatedTime(LocalDateTime.now());
            boolean ok = r008TaskRepository.updateByLogId(patch);
            if (!ok) {
                log.warn("[R008][FAIL] 任務狀態更新失敗：logId={}", match.getLogId());
            } else {
                log.info("[R008][FAIL] 任務已更新為 FAILED/FAIL：logId={}", match.getLogId());
            }
        } catch (Exception e) {
            log.error("[R008][FAIL] 自動上報或更新任務失敗：cmId={}, err={}", containerMainId, e.getMessage(), e);
        }
    }

    /** 僅在 ACK 目標狀態改變時才寫入，並記錄起始時間 */
    private boolean setAckIfChanged(int craneId, boolean value) {
        Boolean prev = lastAckWritten.get(craneId);
        if (prev != null && prev == value) return false;
        plcCraneBitWriter.writeRemoveAccountAck(craneId, value);
        lastAckWritten.put(craneId, value);
        ackSinceMs.put(craneId, System.currentTimeMillis());
        return true;
    }

    /** 每小時 rearm 限流 */
    private boolean underRearmBudget(int craneId, long nowMs) {
        long windowStart = rearmWindowStartMs.getOrDefault(craneId, 0L);
        int count = rearmCountMap.getOrDefault(craneId, 0);
        if (nowMs - windowStart >= 3600_000L) {
            rearmWindowStartMs.put(craneId, nowMs);
            rearmCountMap.put(craneId, 0);
            count = 0;
        }
        if (count >= maxRearmPerHour) return false;
        rearmCountMap.put(craneId, count + 1);
        if (windowStart == 0L) rearmWindowStartMs.put(craneId, nowMs);
        return true;
    }

    /** 解析掃描清單（以 craneId 為單位） */
    private Set<Integer> resolveTargets() {
        if (targetsCsv != null && !targetsCsv.isBlank()) {
            return Arrays.stream(targetsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        Set<Integer> ids = new LinkedHashSet<>();
        for (int i = 1; i <= Math.max(1, scanMaxCraneId); i++) ids.add(i);
        return ids;
    }

    /** 僅以 alias_code 反查容器（給 R008 FAIL 用） */
    private Optional<Long> resolveContainerByAliasCode(String aliasCode) {
        if (StringUtils.isBlank(aliasCode)) return Optional.empty();
        String b = aliasCode.trim();
        return containerMainRepository.findByAliasCode(b).map(ContainerMain::getId);
    }

    /** 由 id 取得 Crane 名稱，避免 magic string */
    private String safeCraneName(int craneId) {
        try {
            return plcCraneRegistry.getCraneNameById(craneId);
        } catch (Exception e) {
            log.warn("[CraneRemove] craneId={} 無對應名稱：{}", craneId, e.toString());
            return null;
        }
    }

    // ========= 取值工具（反射） =========
    private Object tryInvoke(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable e) {
            return null;
        }
    }

    private String tryGetString(Object obj, String... methods) {
        for (String m : methods) {
            Object v = tryInvoke(obj, m);
            if (v instanceof String s && !s.isEmpty()) return s;
        }
        return null;
    }

    // ========= 型別＆字串工具 =========

    /** 去尾端 NUL 與控制字元（log 友善） */
    private String normalizeAscii50(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\0') end--;
        String t = s.substring(0, end);
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x20 && c <= 0x7E) out.append(c);
        }
        return out.toString();
    }

    private String sample(String s) {
        if (s == null) return "";
        return s.length() <= 50 ? s : s.substring(0, 50);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
