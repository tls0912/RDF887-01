package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * GP6RequestGenerator
 * <p>
 * 規則：
 * 1) Site#12：容器「一進 12」要啟動紅外線 #5 量測（verified_quantity），並把 content_kind 設為 ALL_COVER（僅 UNKNOWN 時覆寫）。
 * 2) 監控 Site#26 / Site#37：若站上「有一般片」且「沒有上蓋（工蓋+上蓋==0）」且「未滿容量(22)」，就從 Site#12 取 1 層「蓋」去補（來源必須 still 有蓋）。
 * 2-1) 若 Site#26 / Site#37 同時符合「需要補蓋」，則依 location_tracking.arrived_time 先到先補（FCFS）。
 * 3) 平常待命：空手且無其他動作時，MOVE 到 Site#26 待命。
 * 4) 互鎖：若 OCR#2 位於 Site#12，禁止任何 Move/Pick/Drop 涉及 Site#12（避免干涉）。
 * <p>
 * - 同批判定：改用 OcrVerification（CoverOcrVerificationService.decideFinal）
 * - 以「tray(anchor) vs ref(cover@Site#12)」的驗證結果為準：
 * - WAIT：代表尚未終態（或尚未建立驗證）→ 不動作
 * - PASS：同批可補蓋
 * - BLOCK：不同批 / 不允許 → 不補
 * <p>
 * layerCount 寫入規格：
 * - PICK：layerCount = 來源站「留下」的層數（leaveCount = precount - plannedMoveLayers）
 * - DROP：layerCount = 落料前目標站的現有層數（precount at target）
 * - MOVE：layerCount = 0
 */
@Slf4j
@Component("GP6")
@RequiredArgsConstructor
public class GP6RequestGenerator implements GripperRequestGenerator {

    // ===== Repository / Cache / PLC 依賴 =====
    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerMainRepository containerMainRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;
    private final WorkingBeamRequestRepository workingBeamrequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final GripperStatusCache gripperStatusCache;
    private final PlcAccessService plc;
    private final DeviceProcessStateReader stateReader;

    // OCR 驗證（同批判定）
    private final OcrVerificationRepository ocrVerificationRepository;
    private final CoverOcrVerificationService ocrVerificationService;

    // ===== 站點與裝置常數 =====
    private static final String COVER_POOL = "Site#12"; // 蓋的來源池（ALL_COVER）
    private static final String SITE_A = "Site#26"; // 需要補蓋站（其一）/ 平常待命站
    private static final String SITE_B = "Site#37"; // 需要補蓋站（其二）

    private static final int MAX_PIECES = 22;   // 每站上限（工蓋+上蓋+一般）
    private static final long INFRARED_ID = 5L;   // 紅外線 #5
    private static final long BEAM_ID = 6L;   // 對應 WorkingBeam ID（供互斥判斷使用）

    private static final int LEVEL_SITE12 = 12;   // 若 PLC Level 直對站號
    private static final int LEVEL_SITE26 = 26;   // 待命站 Site#26 的 Level

    // content_kind
    private static final String KIND_ALL_COVER = "ALL_COVER";
    private static final String KIND_UNKNOWN = "UNKNOWN";
    private static final String KIND_NORMAL_NO_COVER = "NORMAL_NO_COVER";
    private static final String KIND_EMPTY = "EMPTY";
    private static final String KIND_NORMAL_WITH_COVER = "NORMAL_WITH_COVER";

    // OCR#2 位址（與 Ocr2Monitor 一致）
    private static final String PLC_DEVICE = "PLC-Packer";
    private static final String OCR2_W_POS_LEVEL = "W13C1"; // Transfer Device Level Position（站點）

    // === 祖先屬性鍵（與 WB5 一致）===
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT = "LINEAGE_ROOT_CMID";

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // ---------------------------------------------------------------------
        // [0] 互斥檢查：紅外線 / 夾爪 / WorkingBeam 皆不可忙碌
        // ---------------------------------------------------------------------
        if (!deviceIsRun("拆併區"))
            return Optional.empty();

        if (infraredBusy(INFRARED_ID) || gripperBusy(gripperId) || workingBeamBusy(BEAM_ID)) {
            //log.debug("[GP6] 忙碌互斥（IR/Gripper/Beam），略過。");
            return Optional.empty();
        }

        boolean ocrAt12 = isOcr2AtSite12(); // 先取一次快照，後續統一使用
        // 取得 Site#12 上的容器（單站一容器假設）
        GP6Context ctx = new GP6Context();
        Optional<Long> cPoolOpt = ctx.getContainerIdBySite(COVER_POOL);
        boolean s12HasTray = cPoolOpt.isPresent();
        Long c12;
        Counts s12 = null;
        if (s12HasTray) {
            c12 = cPoolOpt.get();
            s12 = ctx.countsAt(c12);
        } else {
            c12 = 0L;
        }

        // ---------------------------------------------------------------------
        // [1] 夾爪已持物 → 先做 DROP 決策（優先補到需要蓋的站，否則回放 12）
        //     補蓋站選擇策略：在 Site#26 / Site#37 中，挑「需要補蓋」且 arrivedTime 最早的一顆（FCFS）
        //
        // - 同批判定：改用 OcrVerification（tray(anchor) vs ref=heldCoverCid）
        // ---------------------------------------------------------------------
        Optional<Long> heldOpt = ctx.getContainerIdByGripper(gripperId);
        if (heldOpt.isPresent()) {
            Long heldCid = heldOpt.get();
            Counts heldCounts = ctx.countsAt(heldCid);
            // 手上必須有「蓋」（工蓋+上蓋 > 0），才有意義補到 26/37
            if (heldCounts.covers() > 0) {
                Optional<CoverTarget> targetOpt = chooseCoverTargetByArrivalWithVerification(
                        ctx,
                        heldCid, /*refCover*/ heldCid, /*SITE_A,*/ SITE_B
                );
                if (targetOpt.isPresent()) {
                    CoverTarget t = targetOpt.get();
                    Counts tgtNow = t.counts;
                    int precount = tgtNow.total(); // DROP layerCount 寫「落料前目標站層數」
                    log.info("[GP6] 夾爪持物 → 依 arrivedTime 補蓋(含驗證)，DROP 到：{} (precount={})，手上(工/上/普={}/{}/{})",
                            t.siteName, precount, heldCounts.workCover(), heldCounts.cover(), heldCounts.product());
                    return createDropWithPrecount(gripperId, t.siteName, heldCid, /*plannedDrop*/1, tgtNow);
                }
            }
            // 沒有需要補蓋的站 → 若 12 有位置，DROP 回 12（但 OCR2 佔位時禁止）
            if (s12HasTray) {
                if (ocrAt12) { // OCR2@Site12 interlock
                    //log.debug("[GP6] 夾爪持物 → 原計畫 DROP 回 Site#12，但 OCR#2 正在 Site#12，禁止下發 DROP@12。");
                    return Optional.empty();
                }
                if (s12.total() < MAX_PIECES) {
                    log.info("[GP6] 夾爪持物 → 無站需要補蓋，DROP 回 {}", COVER_POOL);
                    return createDropWithPrecount(gripperId, COVER_POOL, heldCid, /*plannedDrop*/1, s12);
                }
            }
            //log.debug("[GP6] 夾爪持物但無合適 DROP 目標（26/37 不需要或 12 滿載），略過本輪。");
            return Optional.empty();
        }
        // ---------------------------------------------------------------------
        // [2] Site#12：首次進站 → 量測 + 設 ALL_COVER
        // ---------------------------------------------------------------------
        if (s12HasTray) {
            // 2-1) content_kind 設為 ALL_COVER（僅 UNKNOWN 時覆寫）
            try {
                ctx.getContainerDataByCid(c12).ifPresent(cd -> {
                    if (KIND_UNKNOWN.equals(cd.getContentKind())) {
                        containerDataRepository.upsertByContainerMainId(c12, null, null, null, null, KIND_ALL_COVER);
                        ctx.evictContainer(c12);
                        log.warn("[GP6] Site#12 container#{} content_kind 由 UNKNOWN 設為 ALL_COVER", c12);
                    }
                });
            } catch (Exception e) {
                log.warn("[GP6] 設定 content_kind 失敗：container#{} err={}", c12, e.getMessage());
            }
            // 2-2) 若尚未 verified → 需 MOVE 到 12 再送紅外線量測（但 OCR2 佔位時禁止 MOVE@12）
            if (!ctx.hasVerifiedQuantity(c12)) {
                String gn = "Gripper#" + gripperId;
                GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);
                boolean fresh = (ds != null) && ds.isValidAndComplete(3);
                if (!fresh) {
                    //log.debug("[GP6] 夾爪狀態快取無效，略過本輪。");
                    return Optional.empty();
                }
                Integer level = safeGetLevel(ds);
                boolean at12 = (level != null && level == LEVEL_SITE12);
                if (!at12) {
                    if (ocrAt12) { // OCR2@Site12 interlock
                        //log.debug("[GP6] 量測前原需 MOVE 到 Site#12，但 OCR#2 正在 Site#12，禁止 MOVE@12。");
                        return Optional.empty();
                    }
                    log.info("[GP6] 量測前需夾爪到位：目前Level={}，目標Site#{}", level, LEVEL_SITE12);
                    return createMoveTo(gripperId, COVER_POOL);
                }
                // 已在 Site#12，僅送量測（不涉及 PICK/DROP/MOVE@12）
                if (!infraredBusy(INFRARED_ID)) {
                    triggerInfraredMeasure(INFRARED_ID, c12);
                    log.info("[GP6] 已向 Infrared#{} 送出量測請求（Site#12）container#{}", INFRARED_ID, c12);
                }
                return Optional.empty(); // 等量測完成，下輪再評估補蓋
            }
        }
        // ---------------------------------------------------------------------
        // [2.5] Site#37 蓋過多 → 先取 1 片起來
        // ---------------------------------------------------------------------
        if (!ocrAt12 && s12HasTray) {
            Optional<Long> siteBOpt = ctx.getContainerIdBySite(SITE_B);
            if (siteBOpt.isPresent()) {
                Long cid = siteBOpt.get();
                Counts counts = ctx.countsAt(cid);
                // 上蓋數 >= 2
                if (counts.covers() >= 2) {
                    log.info("[GP6] {} 蓋數過多(covers={})，PICK 1 片回收",
                            SITE_B, counts.covers());
                    return createPickWithPrecount(
                            gripperId,
                            SITE_B,
                            COVER_POOL,
                            cid,
                            1,
                            counts,
                            s12
                    );
                }
            }
        }
        // ---------------------------------------------------------------------
        // [3] 監控 Site#26 / Site#37：若無蓋 → 從 12 補 1 片蓋（PICK）
        //     - 來源 Site#12 必須 still 有蓋
        //     - 目標站必須符合「有一般片、無蓋、未滿 22」
        //     - 若 26/37 同時符合，則依 arrivedTime 先到先補
        //
        // ★ 同批判定：改用 OcrVerification（tray(anchor) vs ref=cover@12）
        // ---------------------------------------------------------------------
        if (s12HasTray && !ocrAt12 && s12.covers() > 0) {
            Optional<CoverTarget> targetOpt = chooseCoverTargetByArrivalWithVerification(
                    ctx,
                    c12, /*refCover*/ c12, /*SITE_A,*/ SITE_B
            );
            if (targetOpt.isPresent()) {
                CoverTarget t = targetOpt.get();
                Long tgtCid = t.containerId;
                String tgtName = t.siteName;

                // 最終同批 PASS 才 PICK
                Long anchorTgt = resolveAnchorCmId(tgtCid);
                CoverOcrVerificationService.FinalDecision d = decideFinalOrWait(anchorTgt, c12, COVER_POOL);
                if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                    Counts sTgt = t.counts;
                    int plannedMove = 1; // 每次補 1 層蓋
                    log.info("[GP6] 由 Site#12 補蓋(驗證PASS) → {}，目標 container#{} arrivedTime={} (decision={})",
                            tgtName, tgtCid, t.arrivedTime, d);
                    return createPickWithPrecount(
                            gripperId,
                            COVER_POOL,     // sourceName
                            tgtName,        // targetName
                            c12,            // containerMainId (=來源 c12)
                            plannedMove,
                            s12, sTgt
                    );
                } else {
                    log.info("[GP6] 跳過 PICK 12→{}：驗證非 PASS（decision={} anchorTgt={} refCover={}）",
                            tgtName, d, anchorTgt, c12);
                }
            }
        }
        // ---------------------------------------------------------------------
        // [4] 平常待命：空手、無其他動作 → MOVE 到 Site#26
        // ---------------------------------------------------------------------
        String gn = "Gripper#" + gripperId;
        GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);
        boolean fresh = (ds != null) && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP6] 待命檢查：夾爪狀態快取無效，略過。");
            return Optional.empty();
        }
        Integer curLevel = safeGetLevel(ds);
        if (curLevel == null || curLevel != LEVEL_SITE26) {
            log.info("[GP6] 空手待命：目前 Level={}，建立 MOVE → {} (Level={})", curLevel, SITE_A, LEVEL_SITE26);
            return createMoveTo(gripperId, SITE_A);
        }

        return Optional.empty();
    }

    // ============== OCR#2 互鎖：是否佔用 Site#12 ==============

    private Integer readOcr2Level() {
        try {
            return plc.readInt16(PLC_DEVICE, OCR2_W_POS_LEVEL);
        } catch (Exception e) {
            log.warn("[GP6] 讀取 OCR#2 位置失敗：{}", e.getMessage());
            return null;
        }
    }

    private boolean isOcr2AtSite12() {
        Integer lv = readOcr2Level();
        boolean at = (lv != null && (lv == 0 || lv == LEVEL_SITE12));
        if (at) {
            //log.debug("[GP6] OCR#2 目前在 Site#12(Level={}) → 對 Site#12 的 MOVE/PICK/DROP 禁止。", lv);
        }
        return at;
    }

    // ====================== FCFS 補蓋：以 arrivedTime 挑站 ======================

    private boolean isNeedy(Counts s) {
        return s.product() > 0 && s.covers() == 0 && s.total() < MAX_PIECES;
    }

    /**
     * 依「抵達順序」挑選補蓋目標站（FCFS），並且「必須通過 OcrVerification 同批 PASS」：
     * - tray(anchor) 對 refCoverContainerId 的驗證：
     * - WAIT / BLOCK → 不列入候選
     * - PASS → 候選
     */
    private Optional<CoverTarget> chooseCoverTargetByArrivalWithVerification(GP6Context ctx, Long refCoverContainerId, Long refCoverIdForDecision, String... siteNames) {
        if (refCoverContainerId == null) return Optional.empty();

        List<CoverTarget> candidates = new ArrayList<>();

        for (String siteName : siteNames) {
            Long locId = locationId(siteName);
            if (locId == null) {
                //log.debug("[GP6] chooseCoverTargetByArrival: site {} 找不到對應 LocationPointId，略過。", siteName);
                continue;
            }

            Optional<LocationTracking> ltOpt = locationTrackingRepository.findByLocationPointId(locId);
            if (ltOpt.isEmpty()) continue;

            LocationTracking lt = ltOpt.get();
            Long cid = lt.getContainerMainId();
            if (cid == null) continue;

            Counts counts = ctx.countsAt(cid);
            if (!isNeedy(counts)) continue;

            LocalDateTime arrived = lt.getArrivedTime();
            if (arrived == null) {
                //log.debug("[GP6] {} container#{} 無 arrivedTime，跳過 FCFS 補蓋判斷。", siteName, cid);
                continue;
            }

            Long anchor = resolveAnchorCmId(cid);
            Long refAnchor = resolveAnchorCmId(refCoverIdForDecision);
            CoverOcrVerificationService.FinalDecision d = decideFinalOrWait(anchor, refAnchor, COVER_POOL);
            if (d != CoverOcrVerificationService.FinalDecision.PASS) {
                //log.debug("[GP6] {} container#{} needy 但驗證非 PASS（decision={} anchor={} ref={}）→ 不列入候選",
//                        siteName, cid, d, anchor, refCoverIdForDecision);
                continue;
            }

            candidates.add(new CoverTarget(siteName, cid, counts, arrived));
        }

        if (candidates.isEmpty()) return Optional.empty();
        return candidates.stream().min(Comparator.comparing(CoverTarget::arrivedTime));
    }

    private static final class CoverTarget {
        final String siteName;
        final Long containerId;
        final Counts counts;
        final LocalDateTime arrivedTime;

        CoverTarget(String siteName, Long containerId, Counts counts, LocalDateTime arrivedTime) {
            this.siteName = siteName;
            this.containerId = containerId;
            this.counts = counts;
            this.arrivedTime = arrivedTime;
        }

        LocalDateTime arrivedTime() {
            return arrivedTime;
        }
    }

    // ====================== 層數取得 + 保守推導 ======================

    private Counts countsAt(Long containerMainId, GP6Context ctx) {
        if (containerMainId == null) return new Counts(0, 0, 0);

        ContainerData cd = ctx.getContainerDataByCid(containerMainId).orElse(null);
        if (cd == null) return new Counts(0, 0, 0);

        Integer w = cd.getWorkCoverLayers();
        Integer c = cd.getCoverLayers();
        Integer p = cd.getProductLayers();

        if (w == null || c == null || p == null) {
            int verified = cd.getVerifiedQuantity() == null ? 0 : cd.getVerifiedQuantity();
            String kind = cd.getContentKind();

            if (KIND_ALL_COVER.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = verified;
                if (p == null) p = 0;
            } else if (KIND_NORMAL_NO_COVER.equals(kind) || KIND_EMPTY.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = 0;
                if (p == null) p = verified;
            } else {
                int cover = (verified > 0 ? 1 : 0);
                if (w == null) w = 0;
                if (c == null) c = cover;
                if (p == null) p = Math.max(verified - cover, 0);
            }
        }

        int wi = Math.max(0, w == null ? 0 : w);
        int ci = Math.max(0, c == null ? 0 : c);
        int pi = Math.max(0, p == null ? 0 : p);
        return new Counts(wi, ci, pi);
    }

    // ====================== layerCount 寫入封裝 ======================

    private Optional<Long> createPickWithPrecount(
            Long gripperId,
            String sourceName,
            String targetName,
            Long sourceContainerId,
            int plannedMoveLayers,
            Counts sourceCountsNow,
            Counts targetCountsNow
    ) {
        int precount = sourceCountsNow.total();
        int leaveCount = Math.max(0, precount - Math.max(0, plannedMoveLayers));

        log.info("[GP6] PICK 計畫: {} -> {}，預計搬 {} 層（蓋）；precount={} → leaveCount={}  " +
                        "(來源明細: 工蓋={} 上蓋={} 一般={}; 目標現況: 工蓋={} 上蓋={} 一般={}) | sourceId={}",
                sourceName, targetName, plannedMoveLayers, precount, leaveCount,
                sourceCountsNow.workCover(), sourceCountsNow.cover(), sourceCountsNow.product(),
                targetCountsNow.workCover(), targetCountsNow.cover(), targetCountsNow.product(),
                sourceContainerId);

        return createRequestWithPrecount(gripperId, "PICK", sourceName, targetName, sourceContainerId, leaveCount);
    }

    private Optional<Long> createDropWithPrecount(
            Long gripperId,
            String targetName,
            Long containerMainId,
            int plannedDropFromHeld,
            Counts targetCountsNow
    ) {
        int precount = targetCountsNow.total();
        log.info("[GP6] DROP 計畫: 夾爪 -> {}，預計放 {} 層（蓋）；layerCount(寫入)={} (= {} 工蓋 + {} 上蓋 + {} 一般)",
                targetName, plannedDropFromHeld,
                precount, targetCountsNow.workCover(), targetCountsNow.cover(), targetCountsNow.product());
        return createRequestWithPrecount(gripperId, "DROP", null, targetName, containerMainId, precount);
    }

    private Optional<Long> createRequestWithPrecount(
            Long gripperId, String taskType, String source, String target,
            Long containerMainId, int layerCountToWrite
    ) {
        Long sourceId = (source != null) ? locationId(source) : null;

        Long targetId = locationId(target);

        GripperRequest req = baseRequest(gripperId, taskType, sourceId, targetId, containerMainId);
        req.setSourceLocationName(source);
        req.setTargetLocationName(target);

        int normalized = Math.max(0, Math.min(layerCountToWrite, MAX_PIECES));
        req.setLayerCount(normalized);

        Double trayThickness = resolveTrayThicknessSafe(containerMainId);
        if (trayThickness == null) {
            log.warn("[GP6] 建立 {} 請求失敗：containerMainId={} tray_thickness_mm 解析失敗。", taskType, containerMainId);
            return Optional.empty();
        }
        req.setTargetHeightMm(BigDecimal.valueOf(trayThickness));

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[GP6] 建立 {} 請求成功: {} -> {} containerId={} layerCount(寫入)={}",
                    taskType, source, target, containerMainId, normalized);
            return Optional.of(req.getId());
        }
        log.warn("[GP6] 建立 {} 請求失敗", taskType);
        return Optional.empty();
    }

    // ====================== 共用：互斥 / 量測 / MOVE / base ======================

    private boolean gripperBusy(Long gripperId) {
        return requestRepository.existsUnfinishedRequestForDevice(gripperId)
                || taskRepository.existsUnfinishedTaskForGripper(gripperId);
    }

    private boolean infraredBusy(long infraredId) {
        return infraredRequestRepository.existsUnfinishedRequestForInfrared(infraredId)
                || infraredTaskRepository.existsUnfinishedTaskForInfrared(infraredId);
    }

    private boolean workingBeamBusy(long workingBeamId) {
        return workingBeamrequestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    private boolean hasVerifiedQuantity(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                .orElse(0) > 0;
    }

    private void triggerInfraredMeasure(long infraredId, Long containerMainId) {
        if (infraredBusy(infraredId)) return;
        infraredRequestRepository.createMeasureRequestForContainer(containerMainId, infraredId);
    }

    private Integer safeGetLevel(GripperDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private Optional<Long> createMoveTo(Long gripperId, String targetName) {
        Long targetId = locationId(targetName);

        GripperRequest req = baseRequest(gripperId, "MOVE", null, targetId, null);
        req.setSourceLocationName(null);
        req.setTargetLocationName(targetName);
        req.setLayerCount(0);
        req.setTargetHeightMm(BigDecimal.ZERO);

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[GP6] 建立 MOVE 請求 → {}", targetName);
            return Optional.of(req.getId());
        }
        log.warn("[GP6] 建立 MOVE 請求失敗");
        return Optional.empty();
    }

    private GripperRequest baseRequest(Long gripperId, String taskType, Long sourceId, Long targetId, Long containerMainId) {
        GripperRequest req = new GripperRequest();
        req.setRequestKey(UUID.randomUUID().toString());
        req.setVersion(1);
        req.setRequestSource("SYSTEM");
        req.setGripperId(gripperId);
        req.setTaskType(taskType);
        req.setAccepted("N");
        req.setRequestTime(LocalDateTime.now());
        req.setCreatedTime(LocalDateTime.now());
        req.setSourceLocationId(sourceId);
        req.setTargetLocationId(targetId);
        req.setContainerMainId(containerMainId);
        return req;
    }

    private record Counts(int workCover, int cover, int product) {
        int covers() {
            return workCover + cover;
        }

        int total() {
            return workCover + cover + product;
        }

        public int workCover() {
            return workCover;
        }

        public int cover() {
            return cover;
        }

        public int product() {
            return product;
        }
    }

    // ====================== OcrVerification：Anchor/Decision ======================

    /**
     * 取得 current 的祖先(Anchor) containerId：
     * - 優先用 LINEAGE_ROOT_CMID
     * - 否則沿 LINEAGE_PARENT_CMID 往上追（最多 8 層）
     * - 都找不到 → 回傳自己
     */
    private Long locationId(String name) {
        return GripperLocationCache.requireLocationId(locationPointRepository, name);
    }

    private class GP6Context {
        private final Map<String, Optional<Long>> containerIdBySite = new HashMap<>();
        private final Map<Long, Optional<Long>> containerIdByGripper = new HashMap<>();
        private final Map<Long, Optional<ContainerData>> containerDataByCid = new HashMap<>();
        private final Map<Long, Counts> countsByCid = new HashMap<>();

        Optional<Long> getContainerIdBySite(String siteName) {
            return containerIdBySite.computeIfAbsent(siteName, locationTrackingRepository::findContainerAtLocationName);
        }

        Optional<Long> getContainerIdByGripper(Long gripperId) {
            return containerIdByGripper.computeIfAbsent(gripperId, locationTrackingRepository::findContainerOnGripper);
        }

        Optional<ContainerData> getContainerDataByCid(Long containerMainId) {
            if (containerMainId == null) {
                return Optional.empty();
            }
            return containerDataByCid.computeIfAbsent(containerMainId, containerDataRepository::findByContainerMainId);
        }

        Counts countsAt(Long containerMainId) {
            if (containerMainId == null) {
                return new Counts(0, 0, 0);
            }
            return countsByCid.computeIfAbsent(containerMainId, id -> GP6RequestGenerator.this.countsAt(id, this));
        }

        boolean hasVerifiedQuantity(Long containerMainId) {
            return getContainerDataByCid(containerMainId)
                    .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                    .orElse(0) > 0;
        }

        void evictContainer(Long containerMainId) {
            if (containerMainId == null) {
                return;
            }
            containerDataByCid.remove(containerMainId);
            countsByCid.remove(containerMainId);
        }
    }

    private Long resolveAnchorCmId(Long selfCmId) {
        if (selfCmId == null) return null;

        Optional<Long> rootOpt = readLongAttr(selfCmId, ATTR_ROOT);
        if (rootOpt.isPresent()) return rootOpt.get();

        Long cur = selfCmId;
        Set<Long> visited = new HashSet<>();
        visited.add(cur);

        for (int i = 0; i < 8; i++) {
            Optional<Long> pOpt = readLongAttr(cur, ATTR_PARENT);
            if (pOpt.isEmpty()) break;

            Long p = pOpt.get();
            if (p == null || p <= 0) break;
            if (visited.contains(p)) break;

            visited.add(p);
            cur = p;

            Optional<Long> pRoot = readLongAttr(cur, ATTR_ROOT);
            if (pRoot.isPresent()) return pRoot.get();
        }
        return cur;
    }

    private Optional<Long> readLongAttr(Long cmId, String key) {
        return containerAttrRepository.findOne(cmId, key)
                .map(ContainerAttr::getAttrValue)
                .flatMap(v -> {
                    try {
                        return Optional.of(Long.parseLong(v.trim()));
                    } catch (Exception ignore) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * 取得 anchor 與 refContainer 的驗證決策：
     * - 查不到或未終態：WAIT（保守，不推進）
     * - 終態：PASS/BLOCK（只回這兩種）
     */
    private CoverOcrVerificationService.FinalDecision decideFinalOrWait(Long anchorCmId, Long refContainerId, String refSite) {
        if (anchorCmId == null || refContainerId == null) return CoverOcrVerificationService.FinalDecision.WAIT;

        Optional<OcrVerification> ovOpt =
                ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorCmId, refContainerId);

        if (ovOpt.isEmpty()) {
            //log.debug("[GP6] {} 等待 OCR 流程啟動（anchor={} ref={}）→ WAIT", refSite, anchorCmId, refContainerId);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }

        CoverOcrVerificationService.FinalDecision d =
                ocrVerificationService.decideFinal(anchorCmId, ovOpt.get());

        if (d != CoverOcrVerificationService.FinalDecision.PASS
                && d != CoverOcrVerificationService.FinalDecision.BLOCK) {
            //log.debug("[GP6] {} 等待 OCR 驗證結果（anchor={} ref={} decision={}）→ WAIT", refSite, anchorCmId, refContainerId, d);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }
        return d;
    }

    // ====================== TRAY盤厚度解析 ======================

    private Double resolveTrayThicknessSafe(Long containerMainId) {
        try {
            Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, "tray_thickness_mm");
            String raw = opt.map(ContainerAttr::getAttrValue).orElse(null);
            return parseDecimalPositive(raw);
        } catch (Exception e) {
            log.error("[LAYER] 讀取 tray_thickness_mm 例外：containerMainId={}, err={}", containerMainId, e.getMessage(), e);
            return null;
        }
    }

    private static Double parseDecimalPositive(String raw) {
        if (raw == null) return null;
        String n = raw.trim().replaceAll("[^0-9,\\.\\-]", "");
        if (n.isEmpty()) return null;
        if (n.contains(".") && n.contains(",")) {
            n = n.replace(",", "");
        } else if (n.contains(",") && !n.contains(".")) {
            n = n.replace(',', '.');
        }
        try {
            double v = Double.parseDouble(n);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
