package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.cover.CoverZoneService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.CraneRequestRepository;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.RobotR029TaskRepository;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.RobotR029Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * CoverZoneMonitor
 * ------------------------------------------------------------
 * 控管 R029 公蓋區（兩條線，每條線三個位置：pool / staging / transfer，皆為 ALL_COVER）：
 *
 * 1) 有 R029 任務 (PROCESSING) 時：
 *    - 取該 lane 的 trayType = 目標料號
 *    - 若三格中存在「料號不符」的公蓋 → 等公蓋到 staging 後，透過 reserveForInbound + createInboundRequest 回收入倉；
 *      回收完、三格全空了才會補新的蓋
 *    - 若三格完全沒有公蓋 → 從倉庫挑一顆 part_no == trayType 的蓋，出庫到 staging
 *    - 若三格有且全部是正確料號 → 不動
 *
 * 2) 沒有任何 R029 任務時：
 *    - 若 staging 有公蓋 → 透過 reserveForInbound + createInboundRequest 回收入倉
 *
 * 回收時機：
 *    - 只在公蓋到達 stagingSite (Site#11 / Site#13) 時，才下入倉任務。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverZoneMonitor {

    private final RobotR029TaskRepository r029TaskRepo;
    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final ReservationOrchestrator reservationOrchestrator;
    private final CoverZoneService coverZoneService;

    private final SiteStatusCache siteStatusCache;

    @Value("${app.worker.r029.cover-monitor.enabled:true}")
    private boolean enabled;

    @Value("${app.worker.r029.cover-monitor.interval-ms:1000}")
    private long intervalMs;

    /**
     * 回倉時來源位預留的 TTL（秒）
     */
    @Value("${app.worker.r029.cover-monitor.origin-ttl-seconds:600}")
    private int originTtlSeconds;

    /** 目前兩條：SUB 先、再 MAIN（你可依實際需求調整優先順序） */
    private static final List<CoverLane> LANES = List.of(CoverLane.SUB, CoverLane.MAIN);

    @Scheduled(fixedDelayString = "${app.worker.r029.cover-monitor.interval-ms:1000}")
    public void tick() {
        if (!enabled) return;
        if (isCraneBusy()) return;

        // 每輪最多動一筆，避免一次炸出一堆 crane 任務
        for (CoverLane lane : LANES) {
            try {
                boolean acted = processLane(lane);
                if (acted) break;
            } catch (Exception e) {
                log.warn("[R029Cover] processLane({}) exception: {}", lane.getLaneName(), e.getMessage(), e);
            }
        }
    }

    private boolean processLane(CoverLane lane) {
        // 先看該 lane 是否有 PROCESSING 的任務
        Optional<RobotR029Task> taskOpt = r029TaskRepo.findFirstProcessingByLane(lane.getLaneName());
        boolean hasTask = taskOpt.isPresent();
        String trayTypeUpper = hasTask ? upper(taskOpt.get().getTrayType()) : null;

        CoverZoneService.GroupSnapshot snap = coverZoneService.snapshot(lane);

        boolean hasAnyCover   = coverZoneService.hasAnyCover(snap);
        boolean hasMismatch   = coverZoneService.hasMismatchCover(snap, trayTypeUpper);
        boolean hasMatch      = coverZoneService.hasMatchingCover(snap, trayTypeUpper);

        // 1) 有任務
        if (hasTask) {
            // 1-1 任務存在且有料號不符公蓋 → 優先「回收錯蓋」（只在 staging 時回收）
            if (hasMismatch) {
                if (tryEvictOneFromStaging(lane, trayTypeUpper)) {
                    return true;
                }
                // staging 沒錯蓋可收，就先不補新的蓋，避免把錯蓋留在 pool/transfer 又出新蓋
                return false;
            }

            // 1-2 任務存在、沒有 mismatch
            //     a) 已有同料號公蓋 (hasMatch) → 不動
            //     b) 沒任何公蓋 → 補一顆新蓋
            if (!hasAnyCover && trayTypeUpper != null) {
                if (tryFeedCoverForLane(lane, trayTypeUpper)) {
                    return true;
                }
            }

            // 1-3 任務存在但 trayType 空 → 保守不動
            return false;
        }

        // 2) 沒任務
        //    若 staging 有任意公蓋 → 回收入倉（慢慢把三格清空）
        if (!hasTask && hasAnyCover) {
            if (tryEvictOneFromStaging(lane, null)) {
                return true;
            }
        }

        return false;
    }

    // ------------------------------------------------------------
    // Crane / 倉儲相關動作
    // ------------------------------------------------------------

    /** 起重機忙碌檢查：deviceId=1 / craneId="1" 任一有未完成 → 忙碌 */
    private boolean isCraneBusy() {
        return craneRequestRepository.existsUnfinishedRequestForDevice(1L)
                || craneTaskRepository.existsUnfinishedTaskForCrane("1");
    }

    /**
     * 從 staging 回收一顆蓋回倉儲（入庫）：
     *
     * - trayTypeUpper == null → 沒任務，staging 有蓋就收
     * - trayTypeUpper != null → 有任務，staging 上「料號不符 trayType」才收
     *
     * 入庫流程參考 AutoInboundMonitor：
     *   1) 由 stagingSite 找出 containerId
     *   2) 用 reserveForInbound() 幫這顆預約一個儲位（排除來源位）
     *   3) 成功拿到 toLocationId 後，呼叫 createInboundRequest()
     */
    private boolean tryEvictOneFromStaging(CoverLane lane, String trayTypeUpper) {
        if (isCraneBusy()) return false;

        Optional<Long> cidOpt = coverZoneService.findEvictCandidateAtStaging(lane, trayTypeUpper);
        if (cidOpt.isEmpty()) {
            return false;
        }

        Long containerId = cidOpt.get();

        // 檢查這顆有沒有被別的任務鎖住
        Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
        if (blockedIds.contains(containerId)) {
            //log.debug("[R029Cover] container#{} 已被任務/請求佔用，暫不回收", containerId);
            return false;
        }

        // 來源位：stagingSite (Site#11 / Site#13)
        Long fromLocId = locationPointRepository.findByName(lane.getStagingSite())
                .map(LocationPoint::getId)
                .orElse(null);
        if (fromLocId == null) {
            log.warn("[R029Cover] stagingSite {} 找不到對應 LocationPoint", lane.getStagingSite());
            return false;
        }

        // 先「預約」一個可用儲位（排除來源位），拿到 toLocationId
        var resvOpt = reservationOrchestrator.reserveForInbound(
                containerId,
                Set.of(fromLocId),
                originTtlSeconds,
                "R029_COVER",
                "R029_COVER_EVICT_" + lane.getLaneName()
        );

        if (resvOpt.isEmpty()) {
            log.warn("[R029Cover] 無可用儲位（或被預約），無法建立公蓋入庫任務 (lane={}, staging={})",
                    lane.getLaneName(), lane.getStagingSite());
            return false;
        }

        Long toLocationId = resvOpt.get().getLocationPointId();

        // 建立「入庫」請求（staging → 倉位）
        craneRequestCommandService.createInboundRequest(
                containerId,
                fromLocId,
                toLocationId,
                "R029_COVER_EVICT;lane=" + lane.getLaneName()
        );

        log.info("[R029Cover] 入庫公蓋：lane={} container#{}: {}(loc#{}) → loc#{}",
                lane.getLaneName(), containerId, lane.getStagingSite(), fromLocId, toLocationId);
        return true;
    }

    /**
     * 有任務且三格全空、且需要 trayTypeUpper 的蓋時：
     * 從 ALL_COVER 倉庫裡挑一顆 part_no == trayTypeUpper 的蓋，出庫到該 lane 的 stagingSite。
     */
    private boolean tryFeedCoverForLane(CoverLane lane, String trayTypeUpper) {
        if (isCraneBusy()) return false;

        // 檢查出料位置狀態
        SiteDeviceStatus ds = siteStatusCache.getLatest(lane.getStagingSite());
        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[R029Cover] {} 狀態快取無效，略過此次請求生成", lane.getStagingSite());
            return false;
        }

        if (!ds.isSiteStandby()) {
            //log.debug("[R029Cover] {} 未準備，略過此次請求生成", lane.getStagingSite());
            return false;
        }

        // 取 ALL_COVER 倉內候選
        List<ContainerWithLocation> candidates =
                containerMainRepository.findAllInWarehouseWithLocationAllCover();

        Set<Long> blockedIds =
                containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();

        candidates.removeIf(c -> blockedIds.contains(c.getId()));

        // 只保留 part_no == trayTypeUpper 的公蓋
        candidates.removeIf(c -> {
            String p = upper(c.getPartNo());
            return p == null || !p.equals(trayTypeUpper);
        });

        if (candidates.isEmpty()) {
            //log.debug("[R029Cover] lane={} 補公蓋失敗：倉內無 part_no={} 的 ALL_COVER 候選",
//                    lane.getLaneName(), trayTypeUpper);
            return false;
        }

        // 取第一顆（之後你可以再加排序策略）
        ContainerWithLocation cover = candidates.get(0);

        Long fromLocId = cover.getLocationId();
        Long toStagingId = locationPointRepository.findByName(lane.getStagingSite())
                .map(LocationPoint::getId)
                .orElse(null);
        if (toStagingId == null) {
            log.warn("[R029Cover] stagingSite {} 找不到對應 LocationPoint，無法補公蓋 (lane={})",
                    lane.getStagingSite(), lane.getLaneName());
            return false;
        }

        // 預留來源位（這邊仍然視為「出庫」，所以用 reserveOriginForOutbound）
        reservationOrchestrator.reserveOriginForOutbound(
                cover.getId(),
                fromLocId,
                originTtlSeconds,
                "R029_COVER",
                "COVER_FEED_" + lane.getLaneName()
        );

        craneRequestCommandService.createOutboundRequest(
                cover.getId(),
                fromLocId,
                toStagingId,
                "R029_COVER_FEED;lane=" + lane.getLaneName() + ";trayType=" + trayTypeUpper
        );

        log.info("[R029Cover] 補公蓋：lane={} trayType={} container#{} fromLoc#{} → staging={}",
                lane.getLaneName(), trayTypeUpper, cover.getId(), fromLocId, lane.getStagingSite());
        return true;
    }

    private String upper(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t.toUpperCase(Locale.ROOT);
    }
}
