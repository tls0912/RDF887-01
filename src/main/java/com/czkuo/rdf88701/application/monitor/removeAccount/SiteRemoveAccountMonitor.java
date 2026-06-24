package com.czkuo.rdf88701.application.monitor.removeAccount;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.r029.R029OutputCaptureService;
import com.czkuo.rdf88701.common.enums.ExitType;
import com.czkuo.rdf88701.config.plc.PlcSiteRegistry;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcSiteBitWriter;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R008AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
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
 * SiteRemoveAccountMonitor
 * ------------------------------------------------------------
 * - 監看 removeAccountReq；上緣清帳並回 ACK；下緣關 ACK
 * - 記錄 PLC 端當下的 Product ID（ASCII50）
 * - 防禦機制：req 長時間維持 → 重新觸發 ACK；req=0 但 ACK 高電位太久 → 強制拉低
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SiteRemoveAccountMonitor {

    private final SiteStatusCache siteStatusCache;                       // Read 區（bits/words）快照
    private final PlcSiteBitWriter plcSiteBitWriter;                     // 寫 RemoveAccountAck
    private final PlcSiteRegistry plcSiteRegistry;                       // id <-> name
    private final LocationPointRepository locationPointRepository;       // 位置資訊
    private final LocationFlowRepository locationFlowRepository;         // 位置紀錄
    private final LocationTrackingRepository locationTrackingRepository; // 清空站點帳務

    // --- 清空帳務任務 ---
    private final CraneTaskRepository craneTaskRepository;
    private final TransferTaskRepository transferTaskRepository;
    private final GripperTaskRepository gripperTaskRepository;

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

    @Value("${monitor.site-remove-ack.poll-ms:300}")
    private long pollMs;

    @Value("${monitor.site-remove-ack.stale-threshold-sec:3}")
    private int staleThresholdSec;

    @Value("${monitor.site-remove-ack.scan-max-site-id:42}")
    private int scanMaxSiteId;

    /** "1,2,5"；空字串=1..scanMaxSiteId */
    @Value("${monitor.site-remove-ack.targets:}")
    private String targetsCsv;

    // -------- 防禦與觀測參數 --------
    /** req 高電位在 ACK=1 狀態下持續 N 秒，視為卡住，進行 rearm (ACK 0→1) */
    @Value("${monitor.site-remove-ack.stuck-req-rearm-sec:10}")
    private long stuckReqRearmSec;

    /** 當 req=0 時，ACK=1 維持超過 N 秒則強制拉低 ACK */
    @Value("${monitor.site-remove-ack.ack-force-drop-sec:5}")
    private long ackForceDropSec;

    /** 每站每小時最多 rearm 次數，避免過度抖動 */
    @Value("${monitor.site-remove-ack.max-rearm-per-hour:3}")
    private int maxRearmPerHour;

    // -------- 狀態記錄 --------
    /** 用於偵測上/下緣 */
    private final Map<Integer, Boolean> lastReqMap = new ConcurrentHashMap<>();
    /** 記錄 ACK 最後寫出的值與起始時間 */
    private final Map<Integer, Boolean> lastAckWritten = new ConcurrentHashMap<>();
    private final Map<Integer, Long> ackSinceMs = new ConcurrentHashMap<>();
    /** req=1 開始的時間，用於 stuck 判斷 */
    private final Map<Integer, Long> reqHighSinceMs = new ConcurrentHashMap<>();
    /** 每站 rearm 計數與視窗開始時間 */
    private final Map<Integer, Integer> rearmCountMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> rearmWindowStartMs = new ConcurrentHashMap<>();
    /** 記錄 PLC 端最後一次看到的 Product ID（去 NUL） */
    private final Map<Integer, String> lastPlcProductId = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${monitor.site-remove-ack.poll-ms:400}")
    public void sync() {
        try {
            Set<Integer> targets = resolveTargets();
            if (targets.isEmpty()) return;

            long now = System.currentTimeMillis();
            int clears = 0, ackWrites = 0, rearms = 0, forcedDrops = 0;

            for (Integer siteId : targets) {
                final String siteName = safeSiteName(siteId);
                if (siteName == null) continue;

                // 1) 取現場狀態（略過過期/不完整）
                SiteDeviceStatus s = siteStatusCache.getLatest(siteName);
                if (s == null || !s.isValidAndComplete(staleThresholdSec)) {
                    continue;
                }

                // 記錄 PLC 端的 Product ID（去掉尾端 NUL/控制碼，僅供稽核與 log）
                String plcProduct = normalizeAscii50(s.getProductId());
                if (plcProduct != null) lastPlcProductId.put(siteId, plcProduct);

                boolean req = s.isRemoveAccountReq();
                boolean prevReq = lastReqMap.getOrDefault(siteId, false);
                boolean ackIsHigh = lastAckWritten.getOrDefault(siteId, false);

                // 2) 上緣觸發：0→1
                if (req && !prevReq) {
                    reqHighSinceMs.put(siteId, now);

                    // (1) 先拿「清帳前」的 container 快照（清帳後就找不到了）
                    Optional<Long> cmBefore = locationTrackingRepository.findContainerAtLocationName(siteName);

                    // 目前移除時會將狀態改為 ABORTED，並寫入 closed_time。
                    cmBefore.ifPresent(containerMainId -> {
                        try {
                            boolean ok = containerMainRepository.abort(containerMainId);
                            if (ok) {
                                log.info("[SiteRemove] {} 容器狀態已更新為 ABORTED: containerMainId={}", siteName, containerMainId);
                            } else {
                                log.warn("[SiteRemove] {} 容器狀態更新失敗(可能不存在/未變更): containerMainId={}", siteName, containerMainId);
                            }
                        } catch (Exception e) {
                            log.warn("[SiteRemove] {} 容器狀態更新例外: containerMainId={}, err={}",
                                    siteName, containerMainId, e.toString(), e);
                        }
                    });

                    // (3) 先清帳（冪等，位置為真） → 新增一致性比對
                    boolean cleared = clearLocationTracking(siteName, plcProduct);
                    if (cleared) clears++;
                    else {
                        log.warn("[SiteRemove] {} 清帳失敗，仍回 ACK 以避免卡站 (product='{}')",
                                siteName, sample(plcProduct));
                    }

                    // (4) 依站點對應，嘗試取消「最新且未終結」的任務
                    cmBefore.ifPresent(containerMainId -> {
                        cancelTasksBySiteMapping(siteId, containerMainId, siteName, plcProduct);
                    });

                    // (5) 這些站點觸發 remove 時 → R008 FAIL（比照 R029：容器優先，再用 PLC 條碼） [優化]
                    if (isR008RemoveSite(siteId)) {
                        tryFailR008IfAnyWithFallback(cmBefore.orElse(null), plcProduct, siteName);
                    }

                    // (6) 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029）
                    if (isR029RemoveSite(siteId)) {
                        tryMarkR029Removed(cmBefore.orElse(null), plcProduct, siteName);
                    }

                    // (7) 回 ACK=1
                    if (setAckIfChanged(siteId, true)) ackWrites++;
                }

                // 3) stuck 防禦：req 長時間=1 且 ACK=1 → rearm
                if (req) {
                    long since = reqHighSinceMs.computeIfAbsent(siteId, __ -> now);
                    long sec = (now - since) / 1000;
                    if (ackIsHigh && sec >= stuckReqRearmSec && underRearmBudget(siteId, now)) {
                        log.warn("[SiteRemove] {} req 持續高電位 {}s，執行 rearm ACK (product='{}')",
                                siteName, sec, sample(lastPlcProductId.get(siteId)));
                        ackWrites += setAckIfChanged(siteId, false) ? 1 : 0;
                        ackWrites += setAckIfChanged(siteId, true) ? 1 : 0;
                        reqHighSinceMs.put(siteId, now);
                        rearms++;
                    }
                } else {
                    reqHighSinceMs.remove(siteId);
                }

                // 4) 下緣觸發：1→0（關 ACK）
                if (!req && prevReq) {
                    ackWrites += setAckIfChanged(siteId, false) ? 1 : 0;
                }

                // 5) 強制矯正：req=0 但 ACK 高電位太久 → 拉低
                if (!req && ackIsHigh) {
                    long ackSec = (now - ackSinceMs.getOrDefault(siteId, now)) / 1000;
                    if (ackSec >= ackForceDropSec) {
                        log.info("[SiteRemove] {} req=0 但 ACK 已維持 {}s，高電位過久，強制拉低",
                                siteName, ackSec);
                        ackWrites += setAckIfChanged(siteId, false) ? 1 : 0;
                        forcedDrops++;
                    }
                }

                // 6) 記錄本輪 req
                lastReqMap.put(siteId, req);
            }

            if (clears > 0 || ackWrites > 0 || rearms > 0 || forcedDrops > 0) {
                log.info("[SiteRemove] done: clears={}, ackWrites={}, rearms={}, forcedDrops={}, targets={} (pollMs={})",
                        clears, ackWrites, rearms, forcedDrops, targets.size(), pollMs);
            }
        } catch (Exception e) {
            log.warn("[SiteRemove] ❌ failure: {}", e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // 任務取消邏輯（表驅動）
    // ------------------------------------------------------------

    /** 設備種類 */
    private enum Dev { CRANE, TRANSFER, GRIPPER }

    /** 取消規格：設備種類 + 設備ID + 顯示名 + 是否允許以條碼回推容器（給 Transfer#6 用） */
    private record CancelSpec(Dev dev, int devId, String devName, boolean allowBarcodeFallback) {}

    /** 一條規則：這些 site 命中時 → 套用這個 CancelSpec */
    private record SiteRule(Set<Integer> sites, CancelSpec spec) {}

    /**
     * 站點→設備對應規則：
     * 01. Site#4 Site#8 Site#15        → Crane#1
     * 02. Site#1 Site#2 Site#3         → Transfer#1
     * 03. Site#9 Site#10               → Transfer#3
     * 04. Site#11 Site#12              → Transfer#4
     * 05. Site#13 Site#14              → Transfer#5
     * 06. Site#15 Site#16              → Transfer#6（特殊：若快照無容器，嘗試用 barcode 反查）
     * 07. Site#3 Site#4                → Gripper#1
     * 08. Site#10 Site#23 Site#34      → Gripper#3
     * 09. Site#24 Site#25              → Gripper#4
     * 10. Site#35 Site#36              → Gripper#5
     * 11. Site#12 Site#26 Site#37      → Gripper#6
     * 12. Site#14 Site#27 (Transfer#8) → Gripper#7
     *
     * 註：規則清單：每條只綁定「一種設備動作」與「一組站點」，不把多個設備塞在同一條。
     */
    private static final List<SiteRule> SITE_RULES = List.of(
            // 01. Site#4 Site#8 Site#15 → Crane#1
            new SiteRule(Set.of(4, 8, 15), new CancelSpec(Dev.CRANE,    1, "Crane#1",   false)),

            // 02. Site#1 Site#2 Site#3 → Transfer#1
            new SiteRule(Set.of(1, 2, 3),  new CancelSpec(Dev.TRANSFER, 1, "Transfer#1", false)),

            // 03. Site#9 Site#10 → Transfer#3
            new SiteRule(Set.of(9, 10),    new CancelSpec(Dev.TRANSFER, 3, "Transfer#3", false)),

            // 04. Site#11 Site#12 → Transfer#4
            new SiteRule(Set.of(11, 12),   new CancelSpec(Dev.TRANSFER, 4, "Transfer#4", false)),

            // 05. Site#13 Site#14 → Transfer#5
            new SiteRule(Set.of(13, 14),   new CancelSpec(Dev.TRANSFER, 5, "Transfer#5", false)),

            // 06. Site#15 Site#16 → Transfer#6（特：允許條碼回推）
            new SiteRule(Set.of(15, 16),   new CancelSpec(Dev.TRANSFER, 6, "Transfer#6", true)),

            // 07. Site#3 Site#4 → Gripper#1
            new SiteRule(Set.of(3, 4),     new CancelSpec(Dev.GRIPPER,  1, "Gripper#1",  false)),

            // 08. Site#10 Site#23 Site#34 → Gripper#3
            new SiteRule(Set.of(10, 23, 34), new CancelSpec(Dev.GRIPPER, 3, "Gripper#3",  false)),

            // 09. Site#24 Site#25 → Gripper#4
            new SiteRule(Set.of(24, 25),   new CancelSpec(Dev.GRIPPER,  4, "Gripper#4",  false)),

            // 10. Site#35 Site#36 → Gripper#5
            new SiteRule(Set.of(35, 36),   new CancelSpec(Dev.GRIPPER,  5, "Gripper#5",  false)),

            // 11. Site#12 Site#26 Site#37 → Gripper#6
            new SiteRule(Set.of(12, 26, 37), new CancelSpec(Dev.GRIPPER, 6, "Gripper#6",  false)),

            // 12. Site#14 Site#27 (Transfer#8) → Gripper#7
            new SiteRule(Set.of(14, 27),   new CancelSpec(Dev.GRIPPER,  7, "Gripper#7",  false))
    );

    /** 入口：給 siteId 執行所有命中的規則；容器以清帳前快照為主，必要時依規則允許條碼回推 */
    private void cancelTasksBySiteMapping(int siteId, Long cmIdFromSnapshot, String siteName, String plcProduct) {
        boolean matched = false;
        for (SiteRule rule : SITE_RULES) {
            if (!rule.sites().contains(siteId)) continue;
            matched = true;

            CancelSpec spec = rule.spec();
            Long cmId = cmIdFromSnapshot;

            // 允許以條碼回推（只在 cmId 缺時嘗試；目前僅 Transfer#6 設為 true）
            if (cmId == null && spec.allowBarcodeFallback && StringUtils.isNotBlank(plcProduct)) {
                cmId = resolveContainerByBarcode(plcProduct).orElse(null);
            }

            switch (spec.dev()) {
                case TRANSFER -> {
                    if (cmId != null) cancelLatestTransferTask(cmId, spec.devId(), spec.devName(), siteName, plcProduct);
                    else log.warn("[SiteRemove] {} 無容器可取消 -> {}", siteName, spec.devName());
                }
                case GRIPPER -> {
                    if (cmId != null) cancelLatestGripperTask(cmId, spec.devId(), spec.devName(), siteName, plcProduct);
                    else log.warn("[SiteRemove] {} 無容器可取消 -> {}", siteName, spec.devName());
                }
                case CRANE -> {
                    if (cmId != null) cancelLatestCraneTask(cmId, spec.devName(), siteName, plcProduct);
                    else log.warn("[SiteRemove] {} 無容器可取消 -> {}", siteName, spec.devName());
                }
            }
        }

        if (!matched) {
            //log.debug("[SiteRemove] {} 無任務取消映射（僅清帳）", siteName);
        }
    }

    // ------------------------------------------------------------
    // 取消各類任務（只動未終結）
    // ------------------------------------------------------------

    private static final Set<String> OPEN = Set.of("PENDING","DISPATCHED","IN_PROGRESS","RETRY");

    private void cancelLatestTransferTask(Long cmId, int transferId, String transferName, String siteName, String plcProduct) {
        try {
            Optional<TransferTask> opt = transferTaskRepository.findLatestByContainerAndTransfer((long) transferId, cmId);
            if (opt.isEmpty()) return;

            TransferTask t = opt.get();
            String status = Optional.ofNullable(t.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            t.setTaskStatus("CANCELLED");
            t.setCancelledTime(now);
            t.setDoneTime(now);
            t.setCancelledReason("Cancelled by " + siteName + " RemoveAccountReq -> " + transferName
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));
            transferTaskRepository.update(t);
            log.warn("[SiteRemove] {} 取消 {} 任務 id={} (status={})", siteName, transferName, t.getId(), status);
        } catch (Exception e) {
            log.warn("[SiteRemove] {} 取消 {} 任務例外：{}", siteName, transferName, e.toString(), e);
        }
    }

    private void cancelLatestGripperTask(Long cmId, int gripperId, String gripperName, String siteName, String plcProduct) {
        try {
            Optional<GripperTask> opt = gripperTaskRepository.findLatestByContainerAndGripper((long) gripperId, cmId);
            if (opt.isEmpty()) return;

            GripperTask t = opt.get();
            String status = Optional.ofNullable(t.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            t.setTaskStatus("CANCELLED");
            t.setCancelledTime(now);
            t.setDoneTime(now);
            t.setCancelledReason("Cancelled by " + siteName + " RemoveAccountReq -> " + gripperName
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));
            gripperTaskRepository.update(t);
            log.warn("[SiteRemove] {} 取消 {} 任務 id={} (status={})", siteName, gripperName, t.getId(), status);
        } catch (Exception e) {
            log.warn("[SiteRemove] {} 取消 {} 任務例外：{}", siteName, gripperName, e.toString(), e);
        }
    }

    private void cancelLatestCraneTask(Long cmId, String craneName, String siteName, String plcProduct) {
        try {
            Optional<CraneTask> opt = craneTaskRepository.findLatestByContainerMainId(cmId);
            if (opt.isEmpty()) return;

            CraneTask t = opt.get();
            String status = Optional.ofNullable(t.getTaskStatus()).orElse("");
            if (!OPEN.contains(status.toUpperCase())) return;

            LocalDateTime now = LocalDateTime.now();
            t.setTaskStatus("CANCELLED");
            t.setCancelledTime(now);
            t.setDoneTime(now);
            t.setCancelledReason("Cancelled by " + siteName + " RemoveAccountReq -> " + craneName
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));
            craneTaskRepository.update(t);
            log.warn("[SiteRemove] {} 取消 {} 任務 id={} (status={})", siteName, craneName, t.getId(), status);
        } catch (Exception e) {
            log.warn("[SiteRemove] {} 取消 {} 任務例外：{}", siteName, craneName, e.toString(), e);
        }
    }

    // ------------------------------------------------------------
    // 取消邏輯與 R029 / R008 共通輔助
    // ------------------------------------------------------------

    /** 需要上報 R008 FAIL 的 site（1..8） */
    private boolean isR008RemoveSite(int siteId) {
        return switch (siteId) {
            case 1, 2, 3, 4, 5, 6, 7, 8 -> true;
            default -> false;
        };
    }

    /** 這些站點觸發 remove 時，要把對應容器標記到 R029：REMOVED（若有對應中的 R029） */
    private boolean isR029RemoveSite(int siteId) {
        // 個別：9,10,23,24,25,26,27,34,35,36,37
        return switch (siteId) {
            case 9, 10, 23, 24, 25, 26, 27, 34, 35, 36, 37 -> true;
            default ->
                // 區間：28~33 與 38~42
                    (siteId >= 28 && siteId <= 33) || (siteId >= 38 && siteId <= 42);
        };
    }

    /** 嘗試標記 R029 REMOVED（容器優先，再用 PLC 條碼） */
    private void tryMarkR029Removed(Long cmId, String plcProduct, String siteName) {
        try {
            if (cmId != null) {
                r029OutputCaptureService.markRemovedIfBelongs(cmId);
                log.info("[SiteRemove] {} 標記 R029: REMOVED by containerId={}", siteName, cmId);
            } else if (StringUtils.isNotBlank(plcProduct)) {
                r029OutputCaptureService.markRemovedByCarrierId(plcProduct.trim());
                log.info("[SiteRemove] {} 標記 R029: REMOVED by carrierId='{}'", siteName, sample(plcProduct));
            } else {
                log.warn("[SiteRemove] {} 想標記 R029: REMOVED 但無 container 快照且無 PLC 條碼", siteName);
            }
        } catch (Exception ex) {
            log.warn("[SiteRemove] {} 標記 R029: REMOVED 失敗：{}", siteName, ex.toString(), ex);
        }
    }

    /** 嘗試觸發 R008 FAIL（容器優先，再用 PLC 條碼） */
    private void tryFailR008IfAnyWithFallback(Long cmId, String plcProduct, String siteName) {
        Long finalCmId = cmId;
        if (finalCmId == null && StringUtils.isNotBlank(plcProduct)) {
            finalCmId = resolveContainerByAliasCode(plcProduct).orElse(null);
        }
        if (finalCmId != null) {
            tryFailR008IfAny(finalCmId, siteName, plcProduct);
        } else {
            //log.debug("[SiteRemove] {} 無法比對容器以觸發 R008 FAIL", siteName);
        }
    }

    // ------------------------------------------------------------
    // R008 FAIL（Site#1..#8）
    // ------------------------------------------------------------

    private void tryFailR008IfAny(Long containerMainId, String siteName, String plcProduct) {
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
            out.setResultMessage("Removed by " + siteName + " RemoveAccountReq"
                    + (StringUtils.isBlank(plcProduct) ? "" : (" product=" + sample(plcProduct))));

            JsonNode payload = objectMapper.valueToTree(out);
            logService.recordReturningId("ack/r008/site-auto-fail", logService.getLocalSystem(),
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

    // ------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------

    /**
     * 清空指定站點的 LocationTracking（冪等）
     * 1) 有 tracking → 先把 location_flow 最後一筆補離開時間與 exitType / exitOperator
     * 2) 刪除 tracking
     * 3) 將 location_point.is_occupied 設為 'N'
     *
     * 並在清帳前，比對 PLC 的 ProductId 與 DB container_code / alias_code 是否一致，
     * 不一致仍強制清除，但會寫 warning。
     */
    private boolean clearLocationTracking(String siteName, String plcProduct) {
        try {
            // 目前以 siteName 查詢對應的 LocationPoint。
            LocationPoint p = locationPointRepository.findByName(siteName)
                    .orElse(null);
            if (p == null) {
                log.warn("[SiteRemove] {} 找不到對應 LocationPoint，無法清帳", siteName);
                return false;
            }

            String reasonBase = "PLC RemoveAccountReq site=" + siteName;
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
                            log.warn("[SiteRemove] {} 發現容器不一致: PLC='{}' vs DB(container={}, alias={}), 強制清除",
                                    siteName, sample(plcProduct), containerCode, aliasCode);
                            reason += " (mismatch PLC=" + sample(plcProduct) + ")";
                        } else {
                            log.info("[SiteRemove] {} 容器一致 (PLC='{}')", siteName, sample(plcProduct));
                        }
                    }
                }

                // [Step 1] 將該 container 在此 location 的最後未離開紀錄標示離開
                locationFlowRepository.markExit(
                        tr.getContainerMainId(),
                        p.getId(),
                        LocalDateTime.now(),
                        ExitType.MANUAL,          // 或依需求改 NORMAL/PLC...
                        "SYS-SITE-REMOVE"
                );

                // [Step 2] 刪除 tracking
                locationTrackingRepository.deleteById(tr.getId());
            } else {
                log.info("[SiteRemove] {} 無容器紀錄，直接視為空位清帳", siteName);
            }

            // [Step 3] 點位改為未佔用
            p.setIsOccupied("N");
            p.setUpdatedTime(LocalDateTime.now());
            locationPointRepository.update(p);

            log.info("[SiteRemove] 清帳成功 <- {} ({})", siteName, reason);
            return true;
        } catch (Exception ex) {
            log.warn("[SiteRemove] {} 清帳例外：{}", siteName, ex.toString(), ex);
            return false;
        }
    }

    /** 僅在 ack 目標狀態改變時才寫入 PLC，並記錄起始時間 */
    private boolean setAckIfChanged(int siteId, boolean value) {
        Boolean prev = lastAckWritten.get(siteId);
        if (prev != null && prev == value) {
            return false;
        }
        plcSiteBitWriter.writeRemoveAccountAck(siteId, value);
        lastAckWritten.put(siteId, value);
        ackSinceMs.put(siteId, System.currentTimeMillis());
        return true;
    }

    /** 每站每小時 rearm 限流（簡單的滑動視窗） */
    private boolean underRearmBudget(int siteId, long nowMs) {
        long windowStart = rearmWindowStartMs.getOrDefault(siteId, 0L);
        int count = rearmCountMap.getOrDefault(siteId, 0);
        if (nowMs - windowStart >= 3600_000L) {
            rearmWindowStartMs.put(siteId, nowMs);
            rearmCountMap.put(siteId, 0);
            count = 0;
        }
        if (count >= maxRearmPerHour) return false;
        rearmCountMap.put(siteId, count + 1);
        if (windowStart == 0L) rearmWindowStartMs.put(siteId, nowMs);
        return true;
    }

    /** 解析掃描清單 */
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
        for (int i = 1; i <= Math.max(1, scanMaxSiteId); i++) ids.add(i);
        return ids;
    }

    /** 僅以 alias_code 反查容器（給 R008 FAIL 用） */
    private Optional<Long> resolveContainerByAliasCode(String aliasCode) {
        if (StringUtils.isBlank(aliasCode)) return Optional.empty();
        String b = aliasCode.trim();
        return containerMainRepository.findByAliasCode(b).map(ContainerMain::getId);
    }

    /** Site#15、Site#16、Transfer#6 特例：用條碼反查容器 */
    private Optional<Long> resolveContainerByBarcode(String barcodeMaybe) {
        if (StringUtils.isBlank(barcodeMaybe)) return Optional.empty();
        String b = barcodeMaybe.trim();
        return containerMainRepository.findByContainerCode(b).map(ContainerMain::getId);
        // return containerMainRepository.findByContainerCode(b).map(ContainerMain::getId)
        //         .or(() -> containerMainRepository.findByAliasCode(b).map(ContainerMain::getId));
    }

    /** 由 id 取得 Site 名稱，避免 magic string */
    private String safeSiteName(int siteId) {
        try {
            return plcSiteRegistry.getSiteNameById(siteId);
        } catch (Exception e) {
            log.warn("[SiteRemove] siteId={} 無對應名稱：{}", siteId, e.toString());
            return null;
        }
    }

    // ---------- ASCII50 工具：去除尾端 NUL 與控制字元，僅供稽核/記錄 ----------
    private String normalizeAscii50(String s) {
        if (s == null) return null;
        // 去尾端 NUL
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\0') end--;
        String t = s.substring(0, end);
        // 去控制字元（log 友善）
        StringBuilder out = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= 0x20 && c <= 0x7E) out.append(c);
        }
        return out.toString();
    }

    /** 短顯示（避免把長字串塞爆 log） */
    private String sample(String s) {
        if (s == null) return "";
        return s.length() <= 50 ? s : s.substring(0, 50);
    }


    private static String nz(String s) { return s == null ? "" : s; }
}
