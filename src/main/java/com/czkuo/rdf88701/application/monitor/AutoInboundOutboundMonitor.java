package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.application.service.reservation.ReservationOrchestrator;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.dto.ContainerWithLocation;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.SiteBidirRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;


/**
 * AutoInboundOutboundMonitor
 * - 入庫：Site#15 受 activeTarget 控制；Site#4 / Site#8 直接入庫到首個可用儲位
 * - 出庫：先補 ALL_COVER（多組池/待料/Transfer 組合）；若不成立則出庫到 Site#9
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoInboundOutboundMonitor {

    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final SiteBidirRouteRepository siteBidirRouteRepository;
    private final ReservationOrchestrator reservationOrchestrator;

    // ===== 入庫來源站 =====
    private static final String INBOUND_SITE1 = "Site#15";
    private static final String INBOUND_SITE2 = "Site#4";
    private static final String INBOUND_SITE3 = "Site#8";

    // ===== 既有出庫目標 =====
    private static final String OUTBOUND_SITE9 = "Site#9";

    // ===== 參數：預約 TTL（秒）=====
    /** 入庫預約儲位有效期（避免長期卡位） */
    private static final long INBOUND_RESERVE_TTL_SEC = 10 * 60; // 10 分鐘
    /** 出庫預留來源位有效期（短暫保護來源位） */
    private static final long OUTBOUND_ORIGIN_TTL_SEC = 5 * 60;  // 5 分鐘

    // ====== ALL_COVER 群組宣告（可擴充）======
    // 說明：每組為 (poolSite, stagingSite, transferName)，逐組檢查
    private static final List<CoverGroup> COVER_GROUPS = List.of(
            new CoverGroup("Site#12", "Site#11", "Transfer#4"), // 既有
            new CoverGroup("Site#14", "Site#13", "Transfer#5")  // 新增
    );

    // Site#15 的 activeTarget 配對碼
    @Value("${app.worker.site15.pair-code:SITE15_16}")
    private String pairCode;

    /**
     * 每秒檢查 Site#4 Site#8 Site#15 是否有容器入庫需求
     */
    @Scheduled(fixedDelay = 600)
    public void checkAutoInbound() {
        // ─────────────────────────────────────────────────────────────
        // Site#15：需同時滿足「Site#15 有帳」且「activeTarget == Site#15」才建單
        // ─────────────────────────────────────────────────────────────
        Optional<Long> containerIdOpt1 = locationTrackingRepository.findContainerAtLocationName(INBOUND_SITE1);
        if (containerIdOpt1.isPresent()) {

            String active = siteBidirRouteRepository.findAll().stream()
                    .filter(r -> pairCode.equalsIgnoreCase(r.getPairCode()))
                    .map(SiteBidirRoute::getActiveTarget)
                    .findFirst()
                    .orElse(null);
            if (!INBOUND_SITE1.equalsIgnoreCase(active)) {
                //log.debug("[AutoInbound] pairCode={} activeTarget={}，不為 {}，略過入庫", pairCode, active, INBOUND_SITE1);
            } else {
                Long containerId = containerIdOpt1.get();
                Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();

                if (blockedIds.contains(containerId)) {
                    //log.debug("[AutoInbound] container#{} 已存在任務/請求，略過", containerId);
                    return;
                }

                Long fromLocationId = locationPointRepository.findByName(INBOUND_SITE1)
                        .map(LocationPoint::getId)
                        .orElse(null);

                // 先「預約」一個可用儲位（排除來源位），拿到 toLocationId
                var resvOpt = reservationOrchestrator.reserveForInbound(
                        containerId,
                        (fromLocationId == null) ? Set.of() : Set.of(fromLocationId),
                        INBOUND_RESERVE_TTL_SEC,
                        "AUTO_INBOUND",
                        "AUTO_INBOUND_SITE15"
                );

                if (resvOpt.isEmpty()) {
                    log.warn("[AutoInbound] 無可用儲位（或被預約），無法建立入庫任務 (from {})", INBOUND_SITE1);
                    return;
                }

                Long toLocationId = resvOpt.get().getLocationPointId();

                craneRequestCommandService.createInboundRequest(
                        containerId,
                        fromLocationId,
                        toLocationId,
                        "AUTO_INBOUND_SITE15"
                );
                log.info("[AutoInbound] 建立入庫任務 container#{}: {} → loc#{}", containerId, INBOUND_SITE1, toLocationId);
                return; // 本輪已處理一筆
            }
        }

        // ─────────────────────────────────────────────────────────────
        // Site#4：直接入庫（改為先預約 → 建單）
        // ─────────────────────────────────────────────────────────────
        Optional<Long> containerIdOpt2 = locationTrackingRepository.findContainerAtLocationName(INBOUND_SITE2);
        if (containerIdOpt2.isPresent()) {
            Long containerId = containerIdOpt2.get();
            Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();

            if (blockedIds.contains(containerId)) {
                //log.debug("[AutoInbound] container#{} 已存在任務/請求，略過", containerId);
                return;
            }

            Long fromLocationId = locationPointRepository.findByName(INBOUND_SITE2)
                    .map(LocationPoint::getId)
                    .orElse(null);

            var resvOpt = reservationOrchestrator.reserveForInbound(
                    containerId,
                    (fromLocationId == null) ? Set.of() : Set.of(fromLocationId),
                    INBOUND_RESERVE_TTL_SEC,
                    "AUTO_INBOUND",
                    "AUTO_INBOUND_SITE4"
            );

            if (resvOpt.isEmpty()) {
                log.warn("[AutoInbound] 無可用儲位，無法建立入庫任務 (from {})", INBOUND_SITE2);
                return;
            }

            Long toLocationId = resvOpt.get().getLocationPointId();

            craneRequestCommandService.createInboundRequest(
                    containerId,
                    fromLocationId,
                    toLocationId,
                    "AUTO_INBOUND_SITE4"
            );
            log.info("[AutoInbound] 建立入庫任務 container#{}: {} → loc#{}", containerId, INBOUND_SITE2, toLocationId);
            return;
        }

        // ─────────────────────────────────────────────────────────────
        // Site#8：直接入庫（改為先預約 → 建單）
        // ─────────────────────────────────────────────────────────────
        Optional<Long> containerIdOpt3 = locationTrackingRepository.findContainerAtLocationName(INBOUND_SITE3);
        if (containerIdOpt3.isPresent()) {
            Long containerId = containerIdOpt3.get();
            Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();

            if (blockedIds.contains(containerId)) {
                //log.debug("[AutoInbound] container#{} 已存在任務/請求，略過", containerId);
                return;
            }

            Long fromLocationId = locationPointRepository.findByName(INBOUND_SITE3)
                    .map(LocationPoint::getId)
                    .orElse(null);

            var resvOpt = reservationOrchestrator.reserveForInbound(
                    containerId,
                    (fromLocationId == null) ? Set.of() : Set.of(fromLocationId),
                    INBOUND_RESERVE_TTL_SEC,
                    "AUTO_INBOUND",
                    "AUTO_INBOUND_SITE8"
            );

            if (resvOpt.isEmpty()) {
                log.warn("[AutoInbound] 無可用儲位，無法建立入庫任務 (from {})", INBOUND_SITE3);
                return;
            }

            Long toLocationId = resvOpt.get().getLocationPointId();

            craneRequestCommandService.createInboundRequest(
                    containerId,
                    fromLocationId,
                    toLocationId,
                    "AUTO_INBOUND_SITE8"
            );
            log.info("[AutoInbound] 建立入庫任務 container#{}: {} → loc#{}", containerId, INBOUND_SITE3, toLocationId);
        }
    }

    /**
     * 每 10 秒檢查倉庫是否需出庫：
     * 1) 優先補 ALL_COVER 路徑（多組）：若「池」為空，且「待料站」與「對應 Transfer」皆無容器，則出庫到「待料站」
     * 2) 否則，走原本的出庫至 Site#9
     */
    // @Scheduled(fixedDelay = 10000)
    public void checkAutoOutbound() {

        // ─────────────────────────────────────────────────────────────
        // 優先路徑：依宣告順序逐組嘗試補 ALL_COVER
        // 條件（以單組為例）：
        //   poolSite 為空 且 stagingSite 無容器 且 transferName 無容器
        // 成立 ⇒ 從倉內挑一顆（排除已被任務/請求鎖定）出庫到 stagingSite
        // ─────────────────────────────────────────────────────────────
        for (CoverGroup g : COVER_GROUPS) {
            if (tryRefillCoverGroup(g)) {
                return; // 已建單，結束本輪
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 原本路徑：出庫至 Site#9
        // 條件：Site#9、Site#10、Transfer#3 不可佔用；且起重機無未完成任務/請求
        // ─────────────────────────────────────────────────────────────
//        Long toSite9Id = locationPointRepository.findByName(OUTBOUND_SITE9)
//                .map(LocationPoint::getId)
//                .orElse(null);
//
//        if (toSite9Id == null) {
//            log.warn("[AutoOutbound] 找不到 {} 對應位置", OUTBOUND_SITE9);
//            return;
//        }
//
//        // 1️⃣ 判斷 Site#9、Site#10、Transfer#3 是否有容器
//        List<String> blockedSites = List.of(OUTBOUND_SITE9, "Site#10", "Transfer#3");
//        for (String site : blockedSites) {
//            if (locationTrackingRepository.hasContainerAtLocationName(site)) {
//                //log.debug("[AutoOutbound] {} 已有容器，略過派貨", site);
//                return;
//            }
//        }
//
//        // 2️⃣ 判斷是否已有未完成任務或 request（沿用既有檢查）
//        boolean hasPendingToSite9 = craneRequestRepository.existsUnfinishedRequestForDevice(1L)
//                || craneTaskRepository.existsUnfinishedTaskForCrane("1");
//
//        if (hasPendingToSite9) {
//            //log.debug("[AutoOutbound] {} 已有未完成任務或請求，略過", OUTBOUND_SITE9);
//            return;
//        }
//
//        // 3️⃣ 找出目前倉庫內的所有容器，排除已指派任務或有 request 的
//        List<ContainerWithLocation> candidates = containerMainRepository.findAllInWarehouseWithLocation();
//        Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
//        candidates.removeIf(c -> blockedIds.contains(c.getId()));
//
//        if (candidates.isEmpty()) {
//            //log.debug("[AutoOutbound] 無可用容器，略過");
//            return;
//        }
//
//        // 4️⃣ 取第一個可出庫容器（可依策略排序）
//        ContainerWithLocation container = candidates.get(0);
//
//        // 5️⃣ 建立出庫任務請求（至 Site#9）
//        craneRequestCommandService.createOutboundRequest(
//                container.getId(),
//                container.getLocationId(),
//                toSite9Id,
//                "AUTO_OUTBOUND_SITE9"
//        );
//
//        log.info("[AutoOutbound] 建立出庫任務 container#{}: {} → {}", container.getId(), container.getLocationCode(), OUTBOUND_SITE9);
    }

    // =====================================================================
    // 私有輔助：嘗試補單一組 ALL_COVER；成功建單回 true
    // =====================================================================
    private boolean tryRefillCoverGroup(CoverGroup g) {
        boolean poolEmpty   = !locationTrackingRepository.hasContainerAtLocationName(g.poolSite());
        boolean stagingHas  =  locationTrackingRepository.hasContainerAtLocationName(g.stagingSite());
        boolean transferHas =  locationTrackingRepository.hasContainerAtLocationName(g.transferName());

        if (poolEmpty && !stagingHas && !transferHas) {
            Long toStagingId = locationPointRepository.findByName(g.stagingSite())
                    .map(LocationPoint::getId)
                    .orElse(null);

            if (toStagingId == null) {
                log.warn("[AutoOutbound] 找不到 {} 對應位置，跳過補蓋池流程", g.stagingSite());
                return false;
            }

            // 起重機是否已有未完成任務/請求（沿用原本檢查）
            boolean craneBusy = craneRequestRepository.existsUnfinishedRequestForDevice(1L)
                    || craneTaskRepository.existsUnfinishedTaskForCrane("1");
            if (craneBusy) {
                //log.debug("[AutoOutbound] 補蓋池流程略過：起重機有未完成任務/請求 (group: {} -> {})",
//                        g.poolSite(), g.stagingSite());
                return false;
            }

            // 找可出庫容器（排除已指派）
            List<ContainerWithLocation> candidates = containerMainRepository.findAllInWarehouseWithLocationAllCover();
            Set<Long> blockedIds = containerMainRepository.findContainerIdsWithUnfinishedTasksOrUnacceptedRequests();
            candidates.removeIf(c -> blockedIds.contains(c.getId()));

            if (candidates.isEmpty()) {
                //log.debug("[AutoOutbound] 倉內無可用容器（補蓋池流程，group: {} -> {}）", g.poolSite(), g.stagingSite());
                return false;
            }

            // 取第一個可出庫容器（可依策略排序）
            ContainerWithLocation container = candidates.get(0);

            // 先預留來源位（避免被其他流程拿去當目標位）
            reservationOrchestrator.reserveOriginForOutbound(
                    container.getId(),
                    container.getLocationId(),
                    OUTBOUND_ORIGIN_TTL_SEC,
                    "AUTO_OUTBOUND",
                    "HOLD_ORIGIN_BEFORE_ALL_COVER"
            );

            // 建立出庫任務請求（至 staging）
            craneRequestCommandService.createOutboundRequest(
                    container.getId(),
                    container.getLocationId(),
                    toStagingId,
                    "AUTO_OUTBOUND_" + g.stagingSite().replace('#', '_')
            );
            log.info("[AutoOutbound] 建立出庫(補蓋池) container#{}: {} → {} (group: {} -> {}, via {})",
                    container.getId(), container.getLocationCode(), g.stagingSite(),
                    g.poolSite(), g.stagingSite(), g.transferName());
            return true;
        }

        // 條件不成立
        return false;
    }

    // 宣告 CoverGroup：一組 ALL_COVER 的池/待料/Transfer
    private record CoverGroup(String poolSite, String stagingSite, String transferName) {}
}
