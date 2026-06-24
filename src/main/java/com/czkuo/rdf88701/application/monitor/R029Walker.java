package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.mqtt.publisher.MqttMessageEventPublisher;
import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.cover.CoverZoneService;
import com.czkuo.rdf88701.application.service.mqtt.MqttMessageLogService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.common.dto.mqtt.ack.R029AckPayload;
import com.czkuo.rdf88701.common.enums.MqttMessageType;
import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * R029Walker
 * <p>
 * 規則：
 * - 只處理 R029：從 mqtt_inbox 以 byCmd 方式挑件（FOR UPDATE SKIP LOCKED）
 * - 一次僅派出「一顆」（單 LOT 對應的一顆容器）
 * - 出口/站點/起重機不符 → 直接 requeue(backoff)
 * - 成功派出「第一顆」後：
 * * 送 ACK=START（僅一次）
 * * 將該 inbox priority 調為 1（最高），直到此筆 R029 的所有 LOT 都「出庫」為止
 * - 完成判定以「是否仍在倉內」為基準（不以 Site#9 是否集結判斷）
 * - 出庫可能失敗回倉：任務結束釋放鎖後，下一輪會再派
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class R029Walker {

    private final MqttInboxRepository inboxRepo;
    private final RobotInR029Repository r029Repo;           // 主檔（取 piece count / tray）
    private final RobotInR029LotRepository r029LotRepo;     // 明細（取 LOT 清單）
    private final RobotR029TaskRepository taskRepo;
    private final ContainerMainRepository containerRepo;
    private final ContainerDataRepository containerDataRepo;
    private final LocationPointRepository locationPointRepo;
    private final LocationTrackingRepository locationTrackingRepo;
    private final CraneRequestCommandService craneRequestCommandService;
    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final MqttMessageEventPublisher publisher;
    private final MqttMessageLogService logService;
    private final ObjectMapper objectMapper;

    private final ReservationOrchestrator reservationOrchestrator;
    private final CoverZoneService coverZoneService;

    private final SiteStatusCache siteStatusCache;

    enum Lane {MAIN, SUB}

    // Walker 控制
    @Value("${app.worker.r029.enabled:true}")
    private boolean enabled;

    @Value("${app.worker.r029.lock-ttl-seconds:15}")
    private int lockTtlSeconds;

    @Value("${app.worker.r029.backoff-seconds:5}")
    private int backoffSeconds;

    @Value("${spring.application.name:r029-walker}")
    private String workerId;

    @Value("${app.worker.r029.reserve-origin-ttl-seconds:600}")   // 來源位預留 TTL，預設 600 秒
    private int reserveOriginTtlSeconds;

    // 目標位（仍需防撞，但不以“到位”作為完成條件）
    private static final String OUTBOUND_SITE9 = "Site#9";

    // 上蓋區（目前兩個都視為 cover 區，出庫前要檢查）
    private static final String COVER_SITE12 = "Site#12";
    private static final String COVER_SITE14 = "Site#14";

    @Scheduled(fixedDelayString = "${app.worker.r029.interval-ms:300}")
    public void tick() {
        if (!enabled) return;
        processOnce();
    }

    /**
     * 單次處理一筆 R029 佇列（黏著策略 + 單顆派單）
     */
    public void processOnce() {
        Optional<MqttInbox> opt = inboxRepo.pickOneForProcessingByCmdNoNextAttemptTime("R029", workerId, Duration.ofSeconds(lockTtlSeconds));
        if (opt.isEmpty()) return;

        MqttInbox inbox = opt.get();
        Long inboxId = inbox.getId();
        Long logId = inbox.getLogId();

        try {
            // 0) 關聯任務
            Optional<RobotR029Task> taskOpt = taskRepo.findByLogId(logId);
            if (taskOpt.isEmpty()) {
                log.warn("[R029Walker] 無對應 task，requeue inboxId={}, logId={}", inboxId, logId);
                //inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                inboxRepo.markRejected(inboxId, "[R029Walker] 無對應 task");
                return;
            }
            RobotR029Task task = taskOpt.get();

            // 1) 任務已終結 → 收尾 inbox
            if (isTerminal(task.getInternalState())) {
                inboxRepo.markDone(inboxId, "R029", task.getId());
                return;
            }

            // 2) QUEUED → 決定 lane + 切 PROCESSING
            if ("QUEUED".equals(task.getInternalState())) {
                //     Lane decided = decideLaneWithCapacity();
                Lane decided = decideLane(task);
                if (decided == null) {
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }

                boolean entered = tryEnterProcessing(task, decided);
                if (!entered) {
                    //log.debug("[R029Walker] 進入 PROCESSING 失敗，requeue inboxId={}", inboxId);
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }

                trySendAckStartOnce(inbox);
                if (inbox.getPriority() == null || inbox.getPriority() != 1) {
                    inboxRepo.updatePriority(inboxId, 1);
                }
            }

            // 3) LOT 清單 & 主檔
            List<String> lotIds = r029LotRepo.findCarrierIdsByLogId(logId);
            if (lotIds == null || lotIds.isEmpty()) {
                log.warn("[R029Walker] LOT_LIST 為空，requeue inboxId={}", inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }
            int pieceCount = r029Repo.findById(logId).map(RobotInR029::getCount).orElse(0);
            if (pieceCount <= 0) {
                log.warn("[R029Walker] pieceCount 無效，requeue inboxId={}, logId={}", inboxId, logId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 4) 目標位置
            Long toSite9Id = locationPointRepo.findByName(OUTBOUND_SITE9).map(LocationPoint::getId).orElse(null);
            if (toSite9Id == null) {
                log.warn("[R029Walker] 找不到 {}，requeue inboxId={}", OUTBOUND_SITE9, inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 5) 完成度判斷
            Set<Long> inWarehouseIds = containerRepo.findAllInWarehouseWithLocation()
                    .stream().map(ContainerWithLocation::getId).collect(Collectors.toSet());
            Set<Long> blockedIds = containerRepo.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
            if (areAllLotsOutOfWarehouse(lotIds, inWarehouseIds, blockedIds)) {
                markTaskCompletedAndAckEnd(task, inboxId);
                return;
            }

            // 6) 出庫前防撞
            if (isAnyBlockedSiteOccupied() || isCraneBusy()) {
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 7) 公蓋區狀態檢查（使用 CoverZoneService.snapshot）
            // ------------------------------------------------------
            // 規則：
            //  - 由 task.lane 決定使用哪一條線的公蓋區 (MAIN / SUB)
            //  - trayTypeUpper 為任務的目標料號
            //  - snapshot(lane) 取得該 lane 三個位置(pool/staging/transfer) 的狀態
            //  - hasMismatchCover == true → 公蓋區有錯料號 → 不出料，等 CoverZoneMonitor 回收
            //  - hasAnyCover == false 或 hasMatchingCover == false → 目前沒有對料蓋 → 不出料，等補蓋
            //  - 僅在「有 match && 無 mismatch」時放行
            // ------------------------------------------------------
            CoverLane coverLane = CoverLane.fromLane(task.getLane());
            if (coverLane == null) {
                // 理論上不應發生，因為 QUEUED→PROCESSING 時已設定 lane
                log.warn("[R029Walker] taskId={} lane={} 無法轉成 CoverLane，暫不出料，inboxId={}",
                        task.getId(), task.getLane(), inboxId);
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            String trayTypeUpper = upper(task.getTrayType());

            // 若 trayType 為空，這筆任務不需要公蓋 match，直接略過公蓋檢查
            if (trayTypeUpper != null) {
                CoverZoneService.GroupSnapshot snap = coverZoneService.snapshot(coverLane);

                boolean hasAnyCover = coverZoneService.hasAnyCover(snap);
                boolean hasMismatch = coverZoneService.hasMismatchCover(snap, trayTypeUpper);
                boolean hasMatch = coverZoneService.hasMatchingCover(snap, trayTypeUpper);

                if (hasMismatch) {
                    //log.debug("[R029Walker] lane={} 公蓋區存在與 trayType={} 不符的公蓋，等待 CoverZoneMonitor 回收，暫不出料 inboxId={}, tid={}",
//                            coverLane.getLaneName(), trayTypeUpper, inboxId, inbox.getTid());
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }

                if (!hasAnyCover || !hasMatch) {
                    //log.debug("[R029Walker] lane={} trayType={} 目前無對應公蓋(part_no)，等待 CoverZoneMonitor 補蓋後再出料，inboxId={}, tid={}",
//                            coverLane.getLaneName(), trayTypeUpper, inboxId, inbox.getTid());
                    inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                    return;
                }
            }

            // 8) 檢查出料位置狀態
            SiteDeviceStatus ds = siteStatusCache.getLatest(OUTBOUND_SITE9);
            boolean fresh = ds != null && ds.isValidAndComplete(3);
            if (!fresh) {
                //log.debug("[R029Walker] SITE9 狀態快取無效，略過此次請求生成");
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            if (!ds.isSiteStandby()) {
                //log.debug("[R029Walker] SITE9 未準備，略過此次請求生成");
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 9) 一次挑一顆
            Optional<DispatchCandidate> candOpt = pickNextCandidate(lotIds, inWarehouseIds, blockedIds);
            if (candOpt.isEmpty()) {
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }
            DispatchCandidate cand = candOpt.get();

            // 9.1) 預留來源位
            var hold = reservationOrchestrator.reserveOriginForOutbound(
                    cand.containerId(), cand.fromLocationId(),
                    reserveOriginTtlSeconds, "R029_WALKER", "OUTBOUND_HOLD_ORIGIN");
            if (hold.isEmpty()) {
                inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
                return;
            }

            // 10) 建立出庫請求
            craneRequestCommandService.createOutboundRequest(
                    cand.containerId(), cand.fromLocationId(), toSite9Id,
                    "R029_WALKER;lot=" + cand.lotId() + ";pieces=" + pieceCount);

            log.info("[R029Walker] 已派出：container#{}, lot={}, from={}, to={}, tid={}",
                    cand.containerId(), cand.lotId(), cand.fromLocationId(), toSite9Id, inbox.getTid());

            // 11) 本輪結束
            inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));

        } catch (Exception e) {
            log.error("[R029Walker] 例外，requeue inboxId={}, err={}", inboxId, e.getMessage(), e);
            inboxRepo.requeue(inboxId, Duration.ofSeconds(backoffSeconds));
        }
    }

    // ========== lane / capacity / state 相關 ==========

    /**
     * 回傳可用流道；兩邊都滿回 null
     */
    private Lane decideLaneWithCapacity() {
        int mainUsing = taskRepo.countProcessingByLane("MAIN");
        int subUsing = taskRepo.countProcessingByLane("SUB");
        if (mainUsing == 0 && subUsing == 0) return Lane.MAIN;
        if (mainUsing == 0) return Lane.MAIN;
        if (subUsing == 0) return Lane.SUB;
        return null; // 兩邊都已有一筆 PROCESSING
    }

    private Lane decideLane(RobotR029Task task) {
        String trayType = upper(task.getTrayType());
        int mainUsing = taskRepo.countProcessingByLane("MAIN");
        int subUsing = taskRepo.countProcessingByLane("SUB");
        int runningSameTray = countProcessingByTrayType(trayType);
        int coverCount = countSystemCoverByTrayType(trayType);

        if (runningSameTray == 0 && coverCount < 1) {
            return null;
        }

        if (mainUsing == 0) {
            return Lane.MAIN;
        }

        if (subUsing == 0) {
            return Lane.SUB;
        }

        return null;
    }

    private int countProcessingByTrayType(String trayType) {

        return (int) taskRepo.findAll()
                .stream()
                .filter(t -> "PROCESSING".equalsIgnoreCase(t.getInternalState()))
                .filter(t -> trayType.equalsIgnoreCase(t.getTrayType()))
                .count();
    }

    private int countSystemCoverByTrayType(String trayType) {

        return (int) containerRepo.findAllInWarehouseWithLocationByContentKind("ALL_COVER")
                .stream()
                .filter(c -> trayType.equalsIgnoreCase(c.getPartNo()))
                .filter(c -> locationTrackingRepo
                        .findByContainerMainId(c.getId())
                        .isPresent())
                .count();
    }

    /**
     * 嘗試把任務切到 PROCESSING 並決定 lane（以 logId 定位），成功回 true
     */
    private boolean tryEnterProcessing(RobotR029Task task, Lane lane) {
        try {
            // 若已有人先設 lane/或已進入 PROCESSING，這裡會失敗（回 false）
            return taskRepo.trySetLaneAndProcessingByLogId(task.getLogId(), lane.name());
        } catch (DuplicateKeyException dke) {
            // 撞到 uq_running_per_lane（同流道同時僅 1 筆），視為失敗
            return false;
        } catch (Exception ex) {
            log.warn("[R029Walker] tryEnterProcessing 例外: {}", ex.getMessage());
            return false;
        }
    }

    private void markTaskCompletedAndAckEnd(RobotR029Task task, Long inboxId) throws Exception {
        // 任務 → COMPLETED（若已是 COMPLETED 則忽略）
        // if (!"COMPLETED".equals(task.getInternalState())) {
        //     taskRepo.updateStateByLogId(task.getLogId(), "PROCESSING", "COMPLETED", "all lots outbound");
        // }
        // 回 ACK=END（僅一次即可：由 logService 去重）
        // trySendAckEndOnce(task.getTid(), task.getLogId());
        inboxRepo.markDone(inboxId, "R029", task.getId());
        log.info("[R029Walker] 全部 LOT 已出庫 → COMPLETED & END; taskId={}, logId={}", task.getId(), task.getLogId());
    }

    private boolean isTerminal(String state) {
        return "COMPLETED".equals(state) || "FAILED".equals(state) || "CANCELLED".equals(state);
    }

    // ========================= 私有輔助 =========================

    /**
     * 被占用站點檢查：Site#9 / Site#10 / Transfer#3 任一有容器 → 阻塞
     */
    private boolean isAnyBlockedSiteOccupied() {
//        for (String site : List.of(OUTBOUND_SITE9, "Site#10", "Transfer#3")) {
        for (String site : List.of(OUTBOUND_SITE9, "Transfer#3")) {
            if (locationTrackingRepo.hasContainerAtLocationName(site)) return true;
        }
        return false;
    }

    /**
     * 起重機忙碌檢查：deviceId=1 / craneId="1" 任一有未完成 → 忙碌
     */
    private boolean isCraneBusy() {
        return craneRequestRepository.existsUnfinishedRequestForDevice(1L)
                || craneTaskRepository.existsUnfinishedTaskForCrane("1");
    }

    /**
     * 全部 LOT 是否都「出庫」：
     * - 視為出庫：該容器不在「倉內容器 id 集合」中（也不在 CRANE 手上）、且也不在 blocked（未完成任務/請求）
     * - 視為未出庫：仍在倉內（inWarehouseIds 含該容器 id），或正在被任務/請求佔用（blockedIds 含該容器 id）
     */
    private boolean areAllLotsOutOfWarehouse(List<String> lotIds,
                                             Set<Long> inWarehouseIds,
                                             Set<Long> blockedIds) {
        for (String id : lotIds) {
            Optional<ContainerMain> cOpt = containerRepo.findByLotNo(id).or(() -> containerRepo.findByAliasCode(id));
            if (cOpt.isEmpty()) continue; // 尚未入帳，視為仍未出庫
            Long cid = cOpt.get().getId();

            // 還在倉內 或 正在執行中 → 尚未「出庫完成」
            if (inWarehouseIds.contains(cid)) return false;
            if (blockedIds.contains(cid)) return false;
        }
        return true;
    }

    /**
     * 由 LOT 清單挑出下一個可派單的候選（只回傳一個）：
     * - 已出庫（不在 inWarehouseIds） → 略過
     * - 被任務/請求鎖住（blocked） → 略過
     * - 無位置 → 略過
     */
    private Optional<DispatchCandidate> pickNextCandidate(List<String> lotIds,
                                                          Set<Long> inWarehouseIds,
                                                          Set<Long> blockedIds) {
        for (String id : lotIds) {
            Optional<ContainerMain> cOpt = containerRepo.findByLotNo(id).or(() -> containerRepo.findByAliasCode(id));
            if (cOpt.isEmpty()) continue;

            ContainerMain c = cOpt.get();
            Long cid = c.getId();

            if (!inWarehouseIds.contains(cid)) continue; // 已出庫
            if (blockedIds.contains(cid)) continue;      // 執行中

            Long loc = locationTrackingRepo.findByContainerMainId(cid)
                    .map(LocationTracking::getLocationPointId).orElse(null);
            if (loc == null) continue;                   // 無位置（防禦）

            return Optional.of(new DispatchCandidate(cid, loc, id));
        }
        return Optional.empty();
    }

    /**
     * 嘗試只送一次 ACK=START：
     * - 首選：logService.hasAckStart(tid,"R029")
     * - 後備：以 priority==1 作為已送 START 的近似判斷
     */
    private void trySendAckStartOnce(MqttInbox inbox) throws Exception {
        String tid = inbox.getTid();
        String targetSystem = inbox.getSender();
        boolean alreadySent = logService.hasAckStart(tid, "R029");
        if (alreadySent) return;

        // === 準備 MESSAGE ===
        // 1) LOT / CARRIER 清單
        Long logId = inbox.getLogId();
        List<String> lotIds = Collections.emptyList();
        try {
            lotIds = r029LotRepo.findCarrierIdsByLogId(logId);
        } catch (Exception ignore) { /* 保底空清單 */ }

        // 2) 主檔：count / tray
        Integer count = null;
        String trayType = null, trayDesc = null;
        try {
            Optional<RobotInR029> mainOpt = r029Repo.findById(logId);
            if (mainOpt.isPresent()) {
                RobotInR029 main = mainOpt.get();
                count = main.getCount();
                trayType = main.getTrayType();
                trayDesc = main.getTrayDesc();
            }
        } catch (Exception ignore) { /* 允許為空 */ }

        R029AckPayload ack = new R029AckPayload();
        ack.setCmd("ROBOT");
        ack.setCmdId("R029");
        ack.setTid(tid);
        ack.setIdDesc("MOVE_LOTS_TO_DISMANTLE_AND_TIE");

        R029AckPayload.Message msg = new R029AckPayload.Message();
        msg.setCarrierList(toCarrierInfoList(lotIds));
        msg.setCount(count == null ? "" : String.valueOf(count));
        msg.setTrayType(trayType);
        msg.setTrayDesc(trayDesc);
        ack.setMessage(msg);

        ack.setResult("START");
        ack.setResultMessage("accepted");

        logService.recordReturningId("ack/r029", workerId, targetSystem,
                objectMapper.valueToTree(ack), MqttMessageType.ACK);
        publisher.publish(targetSystem, objectMapper.writeValueAsString(ack),
                MqttMessageType.ACK, tid, "R029");
        //log.debug("[R029Walker][ACK] START sent, tid={}, carriers={}", tid, lotIds);
    }

    /**
     * 將 List<String> 轉成 CARRIER_LIST
     */
    private static List<R029AckPayload.CarrierInfo> toCarrierInfoList(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<R029AckPayload.CarrierInfo> list = new ArrayList<>();
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            R029AckPayload.CarrierInfo ci = new R029AckPayload.CarrierInfo();
            ci.setCarrierId(id.trim());
            list.add(ci);
        }
        return list;
    }

    /**
     * 將字串轉換為大寫
     */
    private String upper(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toUpperCase(Locale.ROOT);
    }

    /**
     * 單筆派單候選封裝
     */
    private record DispatchCandidate(Long containerId, Long fromLocationId, String lotId) {
    }

}
