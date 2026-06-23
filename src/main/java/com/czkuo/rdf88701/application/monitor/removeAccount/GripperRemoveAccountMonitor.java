package com.czkuo.rdf88701.application.monitor.removeAccount;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.plc.PlcGripperRegistry;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcGripperBitWriter;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.GripperTask;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * GripperRemoveAccountMonitor
 * ------------------------------------------------------------
 * - 監看 removeAccountReq；上緣清帳並回 ACK；下緣關 ACK
 * - 記錄 PLC 當下 Product ID（ASCII50）
 * - 防禦機制：req=1 + ACK=1 持續過久 → rearm；req=0 但 ACK=1 過久 → 強制拉低
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperRemoveAccountMonitor {

    private final GripperStatusCache gripperStatusCache;                 // Read 區快照（by name）
    private final PlcGripperBitWriter plcGripperBitWriter;               // 寫 ACK bit
    private final LocationPointRepository locationPointRepository;       // 位置資訊
    private final LocationFlowRepository locationFlowRepository;         // 位置紀錄
    private final LocationTrackingRepository locationTrackingRepository; // 清空站點帳務
    private final PlcGripperRegistry plcGripperRegistry;                 // id <-> name
    private final GripperTaskRepository gripperTaskRepository;           // 清空帳務任務

    // 類別欄位區域（與其他 repository/服務同層）
    private final R029OutputCaptureService r029OutputCaptureService;

    // === R008 FAIL 所需 ===
    private final ContainerMainRepository containerMainRepository;
    private final RobotR008TaskRepository r008TaskRepository;
    private final MqttMessageEventPublisher eventPublisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    @Value("${app.external.ase-system:ase}")
    private String aseSystem;

    // ===== 週期/掃描設定 =====
    @Value("${monitor.gripper-remove-ack.poll-ms:300}")
    private long pollMs;

    @Value("${monitor.gripper-remove-ack.stale-threshold-sec:3}")
    private int staleThresholdSec;

    @Value("${monitor.gripper-remove-ack.scan-max-gripper-id:8}")
    private int scanMaxGripperId;

    /** 例如 "1,2,3"；空字串=1..scanMaxGripperId */
    @Value("${monitor.gripper-remove-ack.targets:}")
    private String targetsCsv;

    // ===== 防禦參數 =====
    /** req=1 且 ACK=1 持續 N 秒，rearm（ACK 0→1） */
    @Value("${monitor.gripper-remove-ack.stuck-req-rearm-sec:10}")
    private long stuckReqRearmSec;

    /** req=0 但 ACK=1 持續 N 秒，強制拉低 */
    @Value("${monitor.gripper-remove-ack.ack-force-drop-sec:5}")
    private long ackForceDropSec;

    /** 每台 gripper 每小時最多 rearm 次數 */
    @Value("${monitor.gripper-remove-ack.max-rearm-per-hour:3}")
    private int maxRearmPerHour;

    // ===== 狀態記錄 =====
    private final Map<Integer, Boolean> lastReqMap = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> lastAckWritten = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ackSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> reqHighSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rearmCountMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> rearmWindowStartMs = new ConcurrentHashMap<>();
    private final Map<Integer, String> lastPlcProductId = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${monitor.gripper-remove-ack.poll-ms:400}")
    public void sync() {
        try {
            Set<Integer> targets = resolveTargets();
            if (targets.isEmpty()) return;

            long now = System.currentTimeMillis();
            int clears = 0, ackWrites = 0, rearms = 0, forcedDrops = 0;

            for (Integer gripperId : targets) {
                final String gripperName = safeGripperName(gripperId);
                if (gripperName == null) continue;

                // 1) 取現場狀態（略過過期/不完整）
                GripperDeviceStatus s = gripperStatusCache.getLatest(gripperName);
                if (s == null || !s.isValidAndComplete(staleThresholdSec)) continue;

                // 記錄 PLC 產品名（去掉尾端 NUL/控制碼）
                String plcProduct = normalizeAscii50(s.getProductId());
                if (plcProduct != null) lastPlcProductId.put(gripperId, plcProduct);

                boolean req = s.isRemoveAccountReq();
                boolean prevReq = lastReqMap.getOrDefault(gripperId, false);
                boolean ackIsHigh = lastAckWritten.getOrDefault(gripperId, false);

                // 2) 上緣：0→1 → 先回推任務取消，再清帳 + ACK=1
                if (req && !prevReq) {
                    reqHighSinceMs.put(gripperId, now);

                    // (1) 回推 containerMainId（清帳前快照）
                    Optional<Long> cmBefore = locationTrackingRepository.findContainerAtLocationName(gripperName);

                    // (2) 移除時應該要變更狀態：ABORTED（寫 closed_time=now）
                    cmBefore.ifPresent(containerMainId -> {
                        try {
                            boolean ok = containerMainRepository.abort(containerMainId);
                            if (ok) {
                                log.info("[GripRemove] {} 容器狀態已更新為 ABORTED: containerMainId={}", gripperName, containerMainId);
                            } else {
                                log.warn("[GripRemove] {} 容器狀態更新失敗(可能不存在/未變更): containerMainId={}", gripperName, containerMainId);
                            }
                        } catch (Exception e) {
                            log.warn("[GripRemove] {} 容器狀態更新例外: containerMainId={}, err={}",
                                    gripperName, containerMainId, e.toString(), e);
                        }
                    });

                    // (3) 先清帳（冪等，位置為真） → 新增一致性比對
                    boolean cleared = clearLocationTracking(gripperName, plcProduct);
                    if (cleared) clears++;
                    else {
                        log.warn("[GripRemove] {} 清帳失敗，仍回 ACK 以避免卡站 (product='{}')",
                                gripperName, sample(plcProduct));
                    }

                    // (4) 依站點對應，嘗試取消「最新且未終結」的任務
                    cmBefore.ifPresent(containerMainId -> {
                        cancelLatestTaskForContainer(gripperId, containerMainId, gripperName, plcProduct);
                    });

                    // (5) 這些站點觸發 remove 時 → R008 FAIL（比照 R029：容器優先，再用 PLC 條碼） [優化]
                    if (isR008RemoveGripper(gripperId)) {
                        tryFailR008IfAnyWithFallback(cmBefore.orElse(null), plcProduct, gripperName);
                    }

                    // (6) 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029）
                    if (isR029RemoveGripper(gripperId)) {
                        tryMarkR029Removed(cmBefore.orElse(null), plcProduct, gripperName);
                    }

                    // (7) 回 ACK=1
                    if (setAckIfChanged(gripperId, true)) ackWrites++;
                }

                // 3) stuck 防禦：req=1 且 ACK=1 過久 → rearm（ACK 0→1）
                if (req) {
                    long since = reqHighSinceMs.computeIfAbsent(gripperId, __ -> now);
                    long sec = (now - since) / 1000;
                    if (ackIsHigh && sec >= stuckReqRearmSec && underRearmBudget(gripperId, now)) {
                        log.warn("[GripRemove] {} req 高電位 {}s，rearm ACK (product='{}')",
                                gripperName, sec, sample(lastPlcProductId.get(gripperId)));
                        ackWrites += setAckIfChanged(gripperId, false) ? 1 : 0;
                        ackWrites += setAckIfChanged(gripperId, true) ? 1 : 0;
                        reqHighSinceMs.put(gripperId, now);
                        rearms++;
                    }
                } else {
                    reqHighSinceMs.remove(gripperId);
                }

                // 4) 下緣：1→0 → 關 ACK
                if (!req && prevReq) {
                    ackWrites += setAckIfChanged(gripperId, false) ? 1 : 0;
                }

                // 5) 矯正：req=0 但 ACK 高電位過久 → 強制拉低
                if (!req && ackIsHigh) {
                    long ackSec = (now - ackSinceMs.getOrDefault(gripperId, now)) / 1000;
                    if (ackSec >= ackForceDropSec) {
                        log.info("[GripRemove] {} req=0 但 ACK 已維持 {}s，強制拉低", gripperName, ackSec);
                        ackWrites += setAckIfChanged(gripperId, false) ? 1 : 0;
                        forcedDrops++;
                    }
                }

                lastReqMap.put(gripperId, req);
            }

            if (clears > 0 || ackWrites > 0 || rearms > 0 || forcedDrops > 0) {
                log.info("[GripRemove] done: clears={}, ackWrites={}, rearms={}, forcedDrops={}, targets={} (pollMs={})",
                        clears, ackWrites, rearms, forcedDrops, targets.size(), pollMs);
            }
        } catch (Exception e) {
            log.warn("[GripRemove] ❌ failure: {}", e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

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
    private boolean clearLocationTracking(String gripperName, String plcProduct) {
        try {
            // 先找到對應的 LocationPoint（假設名稱就是站點名稱 = gripperName）
            LocationPoint p = locationPointRepository.findByName(gripperName)
                    .orElse(null);
            if (p == null) {
                log.warn("[GripRemove] {} 找不到對應 LocationPoint，無法清帳", gripperName);
                return false;
            }

            String reasonBase = "PLC RemoveAccountReq gripper=" + gripperName;
            String reason = reasonBase;

            // 先找 Tracking（找得到才有 containerMainId 可補 flow 離開 & 做 product 比對）
            var trackingOpt = locationTrackingRepository.findByLocationPointId(p.getId());

            if (trackingOpt.isPresent()) {
                var tr = trackingOpt.get();

                // 先比對 PLC Product vs ContainerMain（保留你原本的邏輯）
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
                            log.warn("[GripRemove] {} 發現容器不一致: PLC='{}' vs DB(container={}, alias={}), 強制清除",
                                    gripperName, sample(plcProduct), containerCode, aliasCode);
                            reason += " (mismatch PLC=" + sample(plcProduct) + ")";
                        } else {
                            log.info("[GripRemove] {} 容器一致 (PLC='{}')", gripperName, sample(plcProduct));
                        }
                    }
                }

                // [Step 1] 將該 container 在此 location 的最後未離開紀錄標示離開
                locationFlowRepository.markExit(
                        tr.getContainerMainId(),
                        p.getId(),
                        LocalDateTime.now(),
                        ExitType.MANUAL,          // 或依需求改 NORMAL/PLC...
                        "SYS-GRIPPER-REMOVE"
                );

                // [Step 2] 刪除 tracking
                locationTrackingRepository.deleteById(tr.getId());
            } else {
                log.info("[GripRemove] {} 無容器紀錄，直接視為空位清帳", gripperName);
            }

            // [Step 3] 點位改為未佔用
            p.setIsOccupied("N");
            p.setUpdatedTime(LocalDateTime.now());
            locationPointRepository.update(p);

            log.info("[GripRemove] 清帳成功 <- {} ({})", gripperName, reason);
            return true;
        } catch (Exception ex) {
            log.warn("[GripRemove] {} 清帳例外：{}", gripperName, ex.toString(), ex);
            return false;
        }
    }

    /** 取消該容器最新一筆 Gripper 任務（冪等）
     *  - 僅處理未終結狀態：PENDING/DISPATCHED/IN_PROGRESS/RETRY
     *  - 更新為 CANCELLED，補 cancelledTime / doneTime
     *  - cancelledReason 附 gripperId/gripperName 與（若有）product 方便稽核
     *  - 查不到或已終結則不動；例外僅記錄，不拋出
     */
    private void cancelLatestTaskForContainer(int gripperId,
                                              Long cmIdFromSnapshot,
                                              String gripperName,
                                              String plcProduct) {
        try {
            Optional<GripperTask> opt = gripperTaskRepository.findLatestByContainerAndGripper((long) gripperId, cmIdFromSnapshot);
            if (opt.isEmpty()) return;

            GripperTask task = opt.get();
            String status = Optional.ofNullable(task.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus("CANCELLED");
            task.setCancelledTime(now);
            task.setDoneTime(now);
            task.setCancelledReason(
                    "Cancelled by PLC Gripper RemoveAccountReq (gripperId=" + gripperId
                            + ", gripperName=" + gripperName
                            + (plcProduct == null || plcProduct.isBlank() ? "" : ", product=" + sample(plcProduct))
                            + ")"
            );
            gripperTaskRepository.update(task);
            log.warn("[GripRemove] {} 已取消容器任務 id={} (status={})", gripperName, task.getId(), status);

        } catch (Exception e) {
            log.warn("[GripRemove] {} 嘗試取消任務例外：{}", gripperName, e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // 取消邏輯與 R029 / R008 共通輔助
    // ------------------------------------------------------------

    /** 需要上報 R008 FAIL 的 gripper（1） */
    private boolean isR008RemoveGripper(int gripperId) {
        return switch (gripperId) {
            case 1 -> true;
            default -> false;
        };
    }

    /** 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029） */
    private boolean isR029RemoveGripper(int gripperId) {
        return switch (gripperId) {
            case 3, 4, 5 -> true;
            default -> false;
        };
    }

    /** 嘗試標記 R029 REMOVED（容器優先，再用 PLC 條碼） */
    private void tryMarkR029Removed(Long cmId, String plcProduct, String gripperName) {
        try {
            if (cmId != null) {
                r029OutputCaptureService.markRemovedIfBelongs(cmId);
                log.info("[GripRemove] {} 標記 R029: REMOVED by containerId={}", gripperName, cmId);
            } else if (StringUtils.isNotBlank(plcProduct)) {
                r029OutputCaptureService.markRemovedByCarrierId(plcProduct.trim());
                log.info("[GripRemove] {} 標記 R029: REMOVED by carrierId='{}'", gripperName, sample(plcProduct));
            } else {
                log.warn("[GripRemove] {} 想標記 R029: REMOVED 但無 container 快照且無 PLC 條碼", gripperName);
            }
        } catch (Exception ex) {
            log.warn("[GripRemove] {} 標記 R029: REMOVED 失敗：{}", gripperName, ex.toString(), ex);
        }
    }

    /** 嘗試觸發 R008 FAIL（容器優先，再用 PLC 條碼） */
    private void tryFailR008IfAnyWithFallback(Long cmId, String plcProduct, String gripperName) {
        Long finalCmId = cmId;
        if (finalCmId == null && StringUtils.isNotBlank(plcProduct)) {
            finalCmId = resolveContainerByAliasCode(plcProduct).orElse(null);
        }
        if (finalCmId != null) {
            tryFailR008IfAny(finalCmId, gripperName, plcProduct);
        } else {
            //log.debug("[GripRemove] {} 無法比對容器以觸發 R008 FAIL", gripperName);
        }
    }

    // ------------------------------------------------------------
    // R008 FAIL（Gripper#1）
    // ------------------------------------------------------------

    private void tryFailR008IfAny(Long containerMainId, String gripperName, String plcProduct) {
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
            out.setResultMessage("Removed by " + gripperName + " RemoveAccountReq"
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId("ack/r008/gripper-auto-fail", logService.getLocalSystem(),
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

    /** 僅在 ack 目標狀態改變時才寫入 PLC，並記錄起始時間 */
    private boolean setAckIfChanged(int gripperId, boolean value) {
        Boolean prev = lastAckWritten.get(gripperId);
        if (prev != null && prev == value) return false;
        plcGripperBitWriter.writeRemoveAccountAck(gripperId, value);
        lastAckWritten.put(gripperId, value);
        ackSinceMs.put(gripperId, System.currentTimeMillis());
        return true;
    }

    /** 每小時 rearm 限流 */
    private boolean underRearmBudget(int gripperId, long nowMs) {
        long windowStart = rearmWindowStartMs.getOrDefault(gripperId, 0L);
        int count = rearmCountMap.getOrDefault(gripperId, 0);
        if (nowMs - windowStart >= 3600_000L) {
            rearmWindowStartMs.put(gripperId, nowMs);
            rearmCountMap.put(gripperId, 0);
            count = 0;
        }
        if (count >= maxRearmPerHour) return false;
        rearmCountMap.put(gripperId, count + 1);
        if (windowStart == 0L) rearmWindowStartMs.put(gripperId, nowMs);
        return true;
    }

    /** 解析掃描清單（以 gripperId 為單位） */
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
        for (int i = 1; i <= Math.max(1, scanMaxGripperId); i++) ids.add(i);
        return ids;
    }

    /** 僅以 alias_code 反查容器（給 R008 FAIL 用） */
    private Optional<Long> resolveContainerByAliasCode(String aliasCode) {
        if (StringUtils.isBlank(aliasCode)) return Optional.empty();
        String b = aliasCode.trim();
        return containerMainRepository.findByAliasCode(b).map(ContainerMain::getId);
    }

    /** 由 id 取得 Gripper 名稱，避免 magic string */
    private String safeGripperName(int gripperId) {
        try {
            return plcGripperRegistry.getGripperNameById(gripperId);
        } catch (Exception e) {
            log.warn("[GripRemove] gripperId={} 無對應名稱：{}", gripperId, e.toString());
            return null;
        }
    }

    // ---------- ASCII50 工具：去尾端 NUL 與控制字元 ----------
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

    /** 短顯示，避免 log 爆字 */
    private String sample(String s) {
        if (s == null) return "";
        return s.length() <= 50 ? s : s.substring(0, 50);
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
