package com.czkuo.rdf88701.application.monitor.removeAccount;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.config.plc.PlcTransferRegistry;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcTransferBitWriter;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
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
 * TransferRemoveAccountMonitor
 * ------------------------------------------------------------
 * - 監看 removeAccountReq；上緣清帳並回 ACK；下緣關 ACK
 * - 記錄 PLC 當下 Product ID（ASCII50）
 * - 防禦機制：req=1 + ACK=1 過久 → rearm；req=0 但 ACK=1 過久 → 強制拉低
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferRemoveAccountMonitor {

    private final TransferStatusCache transferStatusCache;                // Read 區快照（by name）
    private final PlcTransferBitWriter plcTransferBitWriter;              // 寫 ACK bit
    private final LocationPointRepository locationPointRepository;        // 位置資訊
    private final LocationFlowRepository locationFlowRepository;          // 位置紀錄
    private final LocationTrackingRepository locationTrackingRepository;  // 清空站點帳務
    private final PlcTransferRegistry plcTransferRegistry;                // id <-> name
    private final TransferTaskRepository transferTaskRepository;          // 清空帳務任務

    // === 為了 Transfer#8 同步檢查是否要刪 Gripper#7 task ===
    private final GripperTaskRepository gripperTaskRepository;

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
    @Value("${monitor.transfer-remove-ack.poll-ms:300}")
    private long pollMs;

    @Value("${monitor.transfer-remove-ack.stale-threshold-sec:3}")
    private int staleThresholdSec;

    @Value("${monitor.transfer-remove-ack.scan-max-transfer-id:9}")
    private int scanMaxTransferId;

    /** 例如 "1,2,3"；空字串=1..scanMaxTransferId */
    @Value("${monitor.transfer-remove-ack.targets:}")
    private String targetsCsv;

    // ===== 防禦參數 =====
    /** req=1 且 ACK=1 維持 N 秒，rearm（ACK 0→1） */
    @Value("${monitor.transfer-remove-ack.stuck-req-rearm-sec:10}")
    private long stuckReqRearmSec;

    /** req=0 但 ACK=1 維持 N 秒，強制拉低 ACK */
    @Value("${monitor.transfer-remove-ack.ack-force-drop-sec:5}")
    private long ackForceDropSec;

    /** 每台 transfer 每小時最多 rearm 次數 */
    @Value("${monitor.transfer-remove-ack.max-rearm-per-hour:3}")
    private int maxRearmPerHour;

    // ===== 狀態記錄 =====
    private final Map<Integer, Boolean> lastReqMap = new ConcurrentHashMap<>();
    private final Map<Integer, Boolean> lastAckWritten = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ackSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> reqHighSinceMs = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> rearmCountMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> rearmWindowStartMs = new ConcurrentHashMap<>();
    private final Map<Integer, String> lastPlcProductId = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${monitor.transfer-remove-ack.poll-ms:400}")
    public void sync() {
        try {
            Set<Integer> targets = resolveTargets();
            if (targets.isEmpty()) return;

            long now = System.currentTimeMillis();
            int clears = 0, ackWrites = 0, rearms = 0, forcedDrops = 0;

            for (Integer transferId : targets) {
                final String transferName = safeTransferName(transferId);
                if (transferName == null) continue;

                // 1) 取現場狀態（略過過期/不完整）
                TransferDeviceStatus s = transferStatusCache.getLatest(transferName);
                if (s == null || !s.isValidAndComplete(staleThresholdSec)) continue;

                // 記錄 PLC 產品名（去掉尾端 NUL/控制碼）
                String plcProduct = normalizeAscii50(s.getProductId());
                if (plcProduct != null) lastPlcProductId.put(transferId, plcProduct);

                boolean req = s.isRemoveAccountReq();
                boolean prevReq = lastReqMap.getOrDefault(transferId, false);
                boolean ackIsHigh = lastAckWritten.getOrDefault(transferId, false);

                // 2) 上緣：0→1 → 先回推任務取消，再清帳 + ACK=1
                if (req && !prevReq) {
                    reqHighSinceMs.put(transferId, now);

                    // (1) 回推 containerMainId（清帳前快照）
                    Optional<Long> cmBefore = locationTrackingRepository.findContainerAtLocationName(transferName);

                    // 目前移除時會將狀態改為 ABORTED，並寫入 closed_time。
                    cmBefore.ifPresent(containerMainId -> {
                        try {
                            boolean ok = containerMainRepository.abort(containerMainId);
                            if (ok) {
                                log.info("[TransRemove] {} 容器狀態已更新為 ABORTED: containerMainId={}", transferName, containerMainId);
                            } else {
                                log.warn("[TransRemove] {} 容器狀態更新失敗(可能不存在/未變更): containerMainId={}", transferName, containerMainId);
                            }
                        } catch (Exception e) {
                            log.warn("[TransRemove] {} 容器狀態更新例外: containerMainId={}, err={}",
                                    transferName, containerMainId, e.toString(), e);
                        }
                    });

                    // (3) 先清帳（冪等，位置為真） → 新增一致性比對
                    boolean cleared = clearLocationTracking(transferName, plcProduct);
                    if (cleared) clears++;
                    else {
                        log.warn("[TransRemove] {} 清帳失敗，仍回 ACK 以避免卡站 (product='{}')",
                                transferName, sample(plcProduct));
                    }

                    // (4) 依站點對應，嘗試取消「最新且未終結」的任務
                    cmBefore.ifPresent(containerMainId -> {
                        cancelLatestTaskForContainer(transferId, containerMainId, transferName, plcProduct);
                    });

                    // (4.5) Transfer#8 特例：同步連帶取消 Gripper#7 最新未終結任務（若存在）
                    if (transferId == 8) {
                        cmBefore.ifPresent(cmId -> {
                            cancelLatestGripperTask(cmId, 7, "Gripper#7", transferName, plcProduct);
                        });
                    }


                    // (5) 這些站點觸發 remove 時 → R008 FAIL（比照 R029：容器優先，再用 PLC 條碼） [優化]
                    if (isR008RemoveTransfer(transferId)) {
                        tryFailR008IfAnyWithFallback(cmBefore.orElse(null), plcProduct, transferName);
                    }

                    // (6) 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029）
                    if (isR029RemoveTransfer(transferId)) {
                        tryMarkR029Removed(cmBefore.orElse(null), plcProduct, transferName);
                    }

                    // (7) 回 ACK=1
                    if (setAckIfChanged(transferId, true)) ackWrites++;
                }

                // 3) stuck 防禦：req=1 且 ACK=1 過久 → rearm（ACK 0→1）
                if (req) {
                    long since = reqHighSinceMs.computeIfAbsent(transferId, __ -> now);
                    long sec = (now - since) / 1000;
                    if (ackIsHigh && sec >= stuckReqRearmSec && underRearmBudget(transferId, now)) {
                        log.warn("[TransRemove] {} req 高電位 {}s，rearm ACK (product='{}')",
                                transferName, sec, sample(lastPlcProductId.get(transferId)));
                        ackWrites += setAckIfChanged(transferId, false) ? 1 : 0;
                        ackWrites += setAckIfChanged(transferId, true) ? 1 : 0;
                        reqHighSinceMs.put(transferId, now);
                        rearms++;
                    }
                } else {
                    reqHighSinceMs.remove(transferId);
                }

                // 4) 下緣：1→0 → 關 ACK
                if (!req && prevReq) {
                    ackWrites += setAckIfChanged(transferId, false) ? 1 : 0;
                }

                // 5) 矯正：req=0 但 ACK 高電位過久 → 強制拉低
                if (!req && ackIsHigh) {
                    long ackSec = (now - ackSinceMs.getOrDefault(transferId, now)) / 1000;
                    if (ackSec >= ackForceDropSec) {
                        log.info("[TransRemove] {} req=0 但 ACK 已維持 {}s，強制拉低", transferName, ackSec);
                        ackWrites += setAckIfChanged(transferId, false) ? 1 : 0;
                        forcedDrops++;
                    }
                }

                lastReqMap.put(transferId, req);
            }

            if (clears > 0 || ackWrites > 0 || rearms > 0 || forcedDrops > 0) {
                log.info("[TransRemove] done: clears={}, ackWrites={}, rearms={}, forcedDrops={}, targets={} (pollMs={})",
                        clears, ackWrites, rearms, forcedDrops, targets.size(), pollMs);
            }
        } catch (Exception e) {
            log.warn("[TransRemove] ❌ failure: {}", e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    private static final Set<String> OPEN = Set.of("PENDING","DISPATCHED","IN_PROGRESS","RETRY");

    /**
     * 清空指定 Transfer 位置的 LocationTracking（冪等）
     * 1) 有 tracking → 先把 location_flow 最後一筆補離開時間與 exitType / exitOperator
     * 2) 刪除 tracking
     * 3) 將 location_point.is_occupied 設為 'N'
     *
     * 並在清帳前，比對 PLC 的 ProductId 與 DB container_code / alias_code 是否一致，
     * 不一致仍強制清除，但會寫 warning。
     */
    private boolean clearLocationTracking(String transferName, String plcProduct) {
        try {
            // 目前以 transferName 查詢對應的 LocationPoint。
            LocationPoint p = locationPointRepository.findByName(transferName)
                    .orElse(null);
            if (p == null) {
                log.warn("[TransRemove] {} 找不到對應 LocationPoint，無法清帳", transferName);
                return false;
            }

            String reasonBase = "PLC RemoveAccountReq transfer=" + transferName;
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
                            log.warn("[TransRemove] {} 發現容器不一致: PLC='{}' vs DB(container={}, alias={}), 強制清除",
                                    transferName, sample(plcProduct), containerCode, aliasCode);
                            reason += " (mismatch PLC=" + sample(plcProduct) + ")";
                        } else {
                            log.info("[TransRemove] {} 容器一致 (PLC='{}')", transferName, sample(plcProduct));
                        }
                    }
                }

                // [Step 1] 將該 container 在此 location 的最後未離開紀錄標示離開
                locationFlowRepository.markExit(
                        tr.getContainerMainId(),
                        p.getId(),
                        LocalDateTime.now(),
                        ExitType.MANUAL,          // 或依需求改 NORMAL/PLC...
                        "SYS-TRANSFER-REMOVE"
                );

                // [Step 2] 刪除 tracking
                locationTrackingRepository.deleteById(tr.getId());
            } else {
                log.info("[TransRemove] {} 無容器紀錄，直接視為空位清帳", transferName);
            }

            // [Step 3] 點位改為未佔用
            p.setIsOccupied("N");
            p.setUpdatedTime(LocalDateTime.now());
            locationPointRepository.update(p);

            log.info("[TransRemove] 清帳成功 <- {} ({})", transferName, reason);
            return true;
        } catch (Exception ex) {
            log.warn("[TransRemove] {} 清帳例外：{}", transferName, ex.toString(), ex);
            return false;
        }
    }

    /** 取消該容器最新一筆 Transfer 任務（冪等）
     *  - 僅處理未終結狀態：PENDING/DISPATCHED/IN_PROGRESS/RETRY
     *  - 更新為 CANCELLED，補 cancelledTime / doneTime
     *  - cancelledReason 附 transferId/transferName 與（若有）product 方便稽核
     *  - 查不到或已終結則不動；例外僅記錄，不拋出
     */
    private void cancelLatestTaskForContainer(int transferId,
                                              Long cmIdFromSnapshot,
                                              String transferName,
                                              String plcProduct) {
        try {
            Optional<TransferTask> opt = transferTaskRepository.findLatestByContainerAndTransfer((long) transferId, cmIdFromSnapshot);
            if (opt.isEmpty()) return;

            TransferTask task = opt.get();
            String status = Optional.ofNullable(task.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus("CANCELLED");
            task.setCancelledTime(now);
            task.setDoneTime(now);
            task.setCancelledReason(
                    "Cancelled by PLC Transfer RemoveAccountReq (transferId=" + transferId
                            + ", transferName=" + transferName
                            + (plcProduct == null || plcProduct.isBlank() ? "" : ", product=" + sample(plcProduct))
                            + ")"
            );
            transferTaskRepository.update(task); // 你現有實作會自動寫入 history
            log.warn("[TransRemove] {} 已取消容器任務 id={} (status={})",
                    transferName, task.getId(), status);

        } catch (Exception e) {
            log.warn("[TransRemove] {} 嘗試取消任務例外：{}", transferName, e.toString(), e);
        }
    }

    // 取消該容器在 Gripper#7 的最新未終結任務（若存在）
    private void cancelLatestGripperTask(Long cmId, int gripperId, String gripperName,
                                         String transferName, String plcProduct) {
        try {
            Optional<GripperTask> opt =
                    gripperTaskRepository.findLatestByContainerAndGripper((long) gripperId, cmId);
            if (opt.isEmpty()) return;

            GripperTask t = opt.get();
            String status = Optional.ofNullable(t.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            t.setTaskStatus("CANCELLED");
            t.setCancelledTime(now);
            t.setDoneTime(now);
            t.setCancelledReason(
                    "Cancelled by PLC Transfer RemoveAccountReq (" + transferName + " -> " + gripperName +
                            (plcProduct == null || plcProduct.isBlank() ? "" : ", product=" + sample(plcProduct)) + ")"
            );
            gripperTaskRepository.update(t);
            log.warn("[TransRemove] {} 連帶取消 {} 任務 id={} (status={})",
                    transferName, gripperName, t.getId(), status);

        } catch (Exception e) {
            log.warn("[TransRemove] {} 嘗試連帶取消 {} 任務例外：{}", transferName, gripperName, e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // 取消邏輯與 R029 / R008 共通輔助
    // ------------------------------------------------------------

    /** 需要上報 R008 FAIL 的 transfer（1, 2） */
    private boolean isR008RemoveTransfer(int transferId) {
        return switch (transferId) {
            case 1, 2 -> true;
            default -> false;
        };
    }

    /** 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029） */
    private boolean isR029RemoveTransfer(int transferId) {
        return switch (transferId) {
            case 8, 9 -> true;
            default -> false;
        };
    }

    /** 嘗試標記 R029 REMOVED（容器優先，再用 PLC 條碼） */
    private void tryMarkR029Removed(Long cmId, String plcProduct, String transferName) {
        try {
            if (cmId != null) {
                r029OutputCaptureService.markRemovedIfBelongs(cmId);
                log.info("[TransRemove] {} 標記 R029: REMOVED by containerId={}", transferName, cmId);
            } else if (StringUtils.isNotBlank(plcProduct)) {
                r029OutputCaptureService.markRemovedByCarrierId(plcProduct.trim());
                log.info("[TransRemove] {} 標記 R029: REMOVED by carrierId='{}'", transferName, sample(plcProduct));
            } else {
                log.warn("[TransRemove] {} 想標記 R029: REMOVED 但無 container 快照且無 PLC 條碼", transferName);
            }
        } catch (Exception ex) {
            log.warn("[TransRemove] {} 標記 R029: REMOVED 失敗：{}", transferName, ex.toString(), ex);
        }
    }

    /** 嘗試觸發 R008 FAIL（容器優先，再用 PLC 條碼） */
    private void tryFailR008IfAnyWithFallback(Long cmId, String plcProduct, String transferName) {
        Long finalCmId = cmId;
        if (finalCmId == null && StringUtils.isNotBlank(plcProduct)) {
            finalCmId = resolveContainerByAliasCode(plcProduct).orElse(null);
        }
        if (finalCmId != null) {
            tryFailR008IfAny(finalCmId, transferName, plcProduct);
        } else {
            //log.debug("[TransRemove] {} 無法比對容器以觸發 R008 FAIL", transferName);
        }
    }

    private void tryFailR008IfAny(Long containerMainId,
                                  String transferName,
                                  String plcProduct) {
        try {
            // 取得 carrierId = container_main.alias_code
            String carrierId = containerMainRepository.findById(containerMainId)
                    .map(ContainerMain::getAliasCode)
                    .map(String::trim)
                    .orElse(null);
            if (carrierId == null || carrierId.isEmpty()) {
                //log.debug("[R008][FAIL] 跳過：containerMainId={} 無 carrierId", containerMainId);
                return;
            }

            // 找 open 的 R008（例如 QUEUED/PROCESSING），比對 carrierId
            RobotR008Task match = r008TaskRepository.findOpen().stream()
                    .filter(t -> carrierId.equalsIgnoreCase(nz(t.getCarrierId())))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                //log.debug("[R008][FAIL] 無需上報：找不到 open R008 carrierId={}", carrierId);
                return;
            }

            // 組 FAIL ACK（沿用 R008 的 TID，內容比照 tryFinalizeR008IfAny，只是 RESULT=FAIL）
            R008AckPayload out = new R008AckPayload();
            out.setCmd("ROBOT");
            out.setCmdId("R008");
            out.setTid(match.getTid());
            out.setIdDesc("ROBOT_MOVE_EQP_TO_SCH");

            R008AckPayload.Message m = new R008AckPayload.Message();
            m.setLotId(match.getLotId());
            m.setCarrierId(match.getCarrierId());
            m.setWipName(nz(match.getWipName()));     // 若你沒有，保持空字串即可
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
            out.setResultMessage("Removed by " + transferName + " RemoveAccountReq"
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));

            // 記錄 & 發送
            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId(
                    "ack/r008/auto-fail",
                    logService.getLocalSystem(),
                    aseSystem,
                    payload,
                    MqttMessageType.ACK
            );
            eventPublisher.publish(
                    aseSystem,
                    objectMapper.writeValueAsString(out),
                    MqttMessageType.ACK,
                    out.getTid(),
                    out.getCmdId()
            );
            log.warn("[R008][FAIL→ASE] 已上報 FAIL：tid={}, carrierId={}, transfer={}",
                    out.getTid(), carrierId, transferName);

            // 更新 R008 任務狀態：FAILED / FAIL
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
    private boolean setAckIfChanged(int transferId, boolean value) {
        Boolean prev = lastAckWritten.get(transferId);
        if (prev != null && prev == value) return false;
        plcTransferBitWriter.writeRemoveAccountAck(transferId, value);
        lastAckWritten.put(transferId, value);
        ackSinceMs.put(transferId, System.currentTimeMillis());
        return true;
    }

    /** 每小時 rearm 限流 */
    private boolean underRearmBudget(int transferId, long nowMs) {
        long windowStart = rearmWindowStartMs.getOrDefault(transferId, 0L);
        int count = rearmCountMap.getOrDefault(transferId, 0);
        if (nowMs - windowStart >= 3600_000L) {
            rearmWindowStartMs.put(transferId, nowMs);
            rearmCountMap.put(transferId, 0);
            count = 0;
        }
        if (count >= maxRearmPerHour) return false;
        rearmCountMap.put(transferId, count + 1);
        if (windowStart == 0L) rearmWindowStartMs.put(transferId, nowMs);
        return true;
    }

    /** 解析掃描清單（以 transferId 為單位） */
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
        for (int i = 1; i <= Math.max(1, scanMaxTransferId); i++) ids.add(i);
        return ids;
    }

    /** 僅以 alias_code 反查容器（給 R008 FAIL 用） */
    private Optional<Long> resolveContainerByAliasCode(String aliasCode) {
        if (StringUtils.isBlank(aliasCode)) return Optional.empty();
        String b = aliasCode.trim();
        return containerMainRepository.findByAliasCode(b).map(ContainerMain::getId);
    }

    /** 由 id 取得 Transfer 名稱，避免 magic string */
    private String safeTransferName(int transferId) {
        try {
            return plcTransferRegistry.getTransferNameById(transferId);
        } catch (Exception e) {
            log.warn("[TransRemove] transferId={} 無對應名稱：{}", transferId, e.toString());
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
