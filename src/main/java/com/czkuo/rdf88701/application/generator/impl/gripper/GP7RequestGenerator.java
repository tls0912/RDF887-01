package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;


/**
 * GP7RequestGenerator
 * <p>
 * 規則摘要：
 * 1) Site#14：
 * - 容器「一進 14」若尚未 verified(verified_quantity==null/0)，需以 GP7 搬到 14 並觸發 Infrared#6 量測。
 * - content_kind 若為 UNKNOWN，可選擇性設為 ALL_COVER（視為全蓋盤）。
 * <p>
 * 2) 監控 Site#27 與 Transfer#8(VIRTUAL#12)：
 * - 判定「需要補蓋」條件：
 * product > 0 且 covers(工蓋+上蓋)==0 且 total < 22。
 * - 從 Site#14 取 1 層蓋去補：
 * a) 來源 Site#14 必須仍有「蓋」(covers>0)。
 * b) 目標站符合「需要補蓋」。
 * c) Transfer#8 只有當實際位置在 VIRTUAL#12（Level=212）才允許補蓋。
 * d) ★同批判定改用 OcrVerification（CoverOcrVerificationService.decideFinal）：
 * - 以「tray(anchor) vs ref(cover@Site#14 或 手上cover)」的驗證結果為準
 * WAIT：尚未終態/尚未建立 → 不動作
 * PASS：同批可補蓋
 * BLOCK：不同批/不允許 → 不補
 * - 若 Site#27 與 Transfer#8 同時需要補蓋，依 arrived_time 最早者先補（FCFS）。
 * <p>
 * 3) 互鎖：
 * - 若 OCR#2 位於 Site#14（W13C1==14 or 0），禁止任何 Move/Pick/Drop 涉及 Site#14。
 * <p>
 * 4) 待命：
 * - 無事可做時，夾爪空手且 Idle → MOVE 到 Site#27 待命。
 * <p>
 * layerCount 寫入規格：
 * - PICK：layerCount = 來源站「留下」的層數（leaveCount = precount - plannedMoveLayers）
 * - DROP：layerCount = 落料前目標站的現有層數（precount at target）
 * - MOVE：layerCount = 0
 */
@Slf4j
@Component("GP7")
@RequiredArgsConstructor
public class GP7RequestGenerator implements GripperRequestGenerator {

    // ===== Repository / Cache / PLC 依賴 =====
    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;
    private final GripperStatusCache gripperStatusCache;
    private final TransferStatusCache transferStatusCache;
    private final PlcAccessService plc;
    private final DeviceProcessStateReader stateReader;

    // ===== OCR 驗證（同批判定） =====
    private final OcrVerificationRepository ocrVerificationRepository;
    private final CoverOcrVerificationService ocrVerificationService;

    // ===== 站點與裝置常數 =====
    private static final String COVER_POOL = "Site#14";    // 蓋來源池
    private static final String TARGET_SITE = "Site#27";    // 需要補蓋站（其一）/ 平常待命站
    private static final String TARGET_VIRTUAL = "VIRTUAL#12"; // Transfer#8 在工位 VIRTUAL#12 對應的站點名

    private static final long TRANSFER_ID = 8L; // Transfer#8
    private static final long INFRARED_ID = 6L; // 紅外線 #6
    private static final int MAX_PIECES = 22;

    // PLC Level 對應（依實機 mapping 調整）
    private static final int LEVEL_SITE14 = 14;
    private static final int LEVEL_SITE27 = 27;
    private static final int LEVEL_VIRTUAL_12 = 212;

    // OCR#2 位址（與 Ocr2Monitor 一致）
    private static final String PLC_DEVICE = "PLC-Packer";
    private static final String OCR2_W_POS_LEVEL = "W13C1";

    // content_kind
    private static final String KIND_ALL_COVER = "ALL_COVER";
    private static final String KIND_NORMAL_NO_COVER = "NORMAL_NO_COVER";
    private static final String KIND_EMPTY = "EMPTY";
    private static final String KIND_UNKNOWN = "UNKNOWN";
    private static final String KIND_NORMAL_WITH_COVER = "NORMAL_WITH_COVER";

    // === 祖先屬性鍵（與 WB5 / GP6 一致）===
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT = "LINEAGE_ROOT_CMID";

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // ---------------------------------------------------------------------
        // [0] 互斥檢查：Gripper / Infrared 不可忙碌
        // ---------------------------------------------------------------------
        if (!deviceIsRun("拆併區"))
            return Optional.empty();

        if (gripperBusy(gripperId) || infraredBusy(INFRARED_ID)) {
            //log.debug("[GP7] 忙碌互斥（Gripper/Infrared），略過。");
            return Optional.empty();
        }
        boolean ocrAt14 = isOcr2AtSite14(); // 先讀一次快照
        GP7Context ctx = new GP7Context();
        Optional<Long> c14Opt = ctx.getContainerIdBySite(COVER_POOL);
        boolean s14HasTray = c14Opt.isPresent();
        Long c14;
        Counts s14 = null;
        if (s14HasTray) {
            c14 = c14Opt.get();
            s14 = ctx.countsAt(c14);
        } else {
            c14 = 0L;
        }
        // ---------------------------------------------------------------------
        // [1] 夾爪已持物 → 先做 DROP 決策（改用 OcrVerification 同批判定）
        //     - 若手上有蓋：優先 DROP 到 FCFS 目標（27 或 T8@V12），但必須 PASS
        //     - 若無目標站可補：嘗試 DROP 回 Site#14（需避開 OCR2，且 14 未滿）
        // ---------------------------------------------------------------------
        Optional<Long> heldOpt = ctx.getContainerIdByGripper(gripperId);
        if (heldOpt.isPresent()) {
            Long heldCid = heldOpt.get();
            Counts heldCounts = ctx.countsAt(heldCid);
            if (heldCounts.covers() > 0) {
                Optional<CoverTarget> site27TargetOpt = buildCoverTargetForSite27(ctx);
//                Optional<CoverTarget> t8TargetOpt     = buildCoverTargetForTransfer8();
                Optional<CoverTarget> fcfsOpt = chooseCoverTargetByArrivalWithVerification(
                        /*refCoverId*/ heldCid, site27TargetOpt/*, t8TargetOpt*/
                );
                if (fcfsOpt.isPresent()) {
                    CoverTarget t = fcfsOpt.get();
                    Counts tgtNow = t.counts;
                    log.info("[GP7] 夾爪持物(cover) → FCFS 補蓋(驗證PASS)，DROP 到 {} (container#{}, arrivedTime={}) precount={} handCovers={}",
                            t.siteName, t.containerId, t.arrivedTime, tgtNow.total(), heldCounts.covers());
                    return createDropWithPrecount(gripperId, t.siteName, heldCid, /*plannedDrop*/1, tgtNow);
                }
            }
            // 沒有需要補蓋的站 → 若 Site#14 有位置，DROP 回 Site#14（但 OCR2 佔位時禁止）
            if (s14HasTray) {
                if (ocrAt14) {
                    //log.debug("[GP7] 夾爪持物 → 原計畫 DROP 回 {}，但 OCR#2 佔用 Site#14，禁止 DROP@14。", COVER_POOL);
                    return Optional.empty();
                }
                if (s14.total() < MAX_PIECES) {
                    log.info("[GP7] 夾爪持物 → 無站需要補蓋，DROP 回 {}", COVER_POOL);
                    return createDropWithPrecount(gripperId, COVER_POOL, heldCid, /*plannedDrop*/1, s14);
                }
            }
            //log.debug("[GP7] 夾爪持物但無合適 DROP 目標（27/T8 不需要或 14 滿載/互鎖），略過本輪。");
            return Optional.empty();
        }

        // -----------------------------------------------------------------
        // [2] Site#14：首次進站未驗證 → 需 MOVE 到 14 並觸發紅外線量測（需避開 OCR#2）
        // -----------------------------------------------------------------
        if (s14HasTray) {
            // 2-1) content_kind 若為 UNKNOWN，可視為 ALL_COVER
            try {
                ctx.getContainerDataByCid(c14).ifPresent(cd -> {
                    if (KIND_UNKNOWN.equals(cd.getContentKind())) {
                        containerDataRepository.upsertByContainerMainId(c14, null, null, null, null, KIND_ALL_COVER);
                        ctx.evictContainer(c14);
                        log.warn("[GP7] Site#14 container#{} content_kind 由 UNKNOWN 設為 ALL_COVER", c14);
                    }
                });
            } catch (Exception e) {
                log.warn("[GP7] 設定 content_kind 失敗：container#{} err={}", c14, e.getMessage());
            }
            // 2-2) 尚未 verified → 先將 GP7 移動到 14，再觸發紅外線量測
            if (!ctx.hasVerifiedQuantity(c14)) {
                String gn = "Gripper#" + gripperId;
                GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);
                boolean fresh = (ds != null) && ds.isValidAndComplete(3);
                if (!fresh) {
                    //log.debug("[GP7] 夾爪狀態快取無效，略過本輪。");
                    return Optional.empty();
                }
                Integer level = safeGetLevel(ds);
                boolean at14 = (level != null && level == LEVEL_SITE14);
                if (!at14) {
                    if (ocrAt14) {
                        log.info("[GP7] 量測前原需 MOVE 到 {}，但 OCR#2 在 Site#14，禁止 MOVE@14。", COVER_POOL);
                        return Optional.empty();
                    }
                    log.info("[GP7] 量測前需夾爪到位：目前Level={}，目標Site#{}", level, LEVEL_SITE14);
                    return createMoveTo(gripperId, COVER_POOL);
                }
                // 已在 Site#14，僅送量測（不涉及 PICK/DROP/MOVE@14）
                if (!infraredBusy(INFRARED_ID)) {
                    triggerInfraredMeasure(INFRARED_ID, c14);
                    log.info("[GP7] 已向 Infrared#{} 送出量測請求（Site#14）container#{}", INFRARED_ID, c14);
                }
                return Optional.empty(); // 等量測完成，下輪再評估補蓋
            }
        }
        // ---------------------------------------------------------------------
        // [2.5] Site#27 蓋過多 → 先取 1 片起來
        // ---------------------------------------------------------------------
        if (!ocrAt14 && s14HasTray) {
            Optional<Long> siteTargetOpt = ctx.getContainerIdBySite(TARGET_SITE);
            if (siteTargetOpt.isPresent()) {
                Long cid = siteTargetOpt.get();
                Counts counts = ctx.countsAt(cid);
                // 上蓋數 >= 2
                if (counts.covers() >= 2) {
                    log.info("[GP7] {} 蓋數過多(covers={})，PICK 1 片回收",
                            TARGET_SITE, counts.covers());
                    return createPickWithPrecount(
                            gripperId,
                            TARGET_SITE,
                            COVER_POOL,
                            cid,
                            1,
                            counts,
                            s14
                    );
                }
            }
        }
        // -----------------------------------------------------------------
        // [3] 夾爪空手 → 從 Site#14 PICK 1 層蓋，補到 FCFS 目標（27 或 T8@V12）
        //     ★ 同批判定改用 OcrVerification（anchor(targetTray) vs ref=cover@14）
        // -----------------------------------------------------------------
        if (s14HasTray && !ocrAt14 && s14.covers() > 0) {
            Optional<CoverTarget> site27TargetOpt = buildCoverTargetForSite27(ctx);
//                    Optional<CoverTarget> t8TargetOpt     = buildCoverTargetForTransfer8();
            Optional<CoverTarget> fcfsOpt = chooseCoverTargetByArrivalWithVerification(
                    /*refCoverId*/ c14, site27TargetOpt/*, t8TargetOpt*/
            );
            if (fcfsOpt.isPresent()) {
                CoverTarget t = fcfsOpt.get();
                log.info("[GP7] 由 {} 補蓋(驗證PASS) → {}，目標 container#{} arrivedTime={}",
                        COVER_POOL, t.siteName, t.containerId, t.arrivedTime);
                return createPickWithPrecount(
                        gripperId,
                        COVER_POOL,
                        t.siteName,  // Site#27 or VIRTUAL#12
                        c14,         // 來源 cover container
                        1,
                        s14,
                        t.counts
                );
            }
        }
        //log.debug("[GP7] Site#14 已無可用『蓋』，暫不補。");

        // ---------------------------------------------------------------------
        // [4] 無事可做 → 平常 MOVE 到 Site#27 待命
        // ---------------------------------------------------------------------
        maybeStandbyAtSite27(gripperId);
        return Optional.empty();
    }

// ====================== FCFS 補蓋候選：27 / T8@V12 ======================

    /**
     * 判斷某容器層數是否符合「需要補蓋」條件。
     */
    private boolean isNeedy(Counts s) {
        return s.product() > 0 && s.covers() == 0 && s.total() < MAX_PIECES;
    }

    /**
     * 建立 Site#27 的補蓋候選。
     */
    private Optional<CoverTarget> buildCoverTargetForSite27(GP7Context ctx) {
        Optional<Long> c27Opt = ctx.getContainerIdBySite(TARGET_SITE);
        if (c27Opt.isEmpty()) {
            return Optional.empty();
        }

        Long cid = c27Opt.get();
        Counts counts = ctx.countsAt(cid);
        if (!isNeedy(counts)) {
            return Optional.empty();
        }

        Optional<LocationTracking> ltOpt = locationTrackingRepository.findByContainerMainId(cid);
        if (ltOpt.isEmpty() || ltOpt.get().getArrivedTime() == null) {
            //log.debug("[GP7] {} container#{} 無 arrivedTime，跳過 FCFS 判斷。", TARGET_SITE, cid);
            return Optional.empty();
        }

        LocalDateTime arrived = ltOpt.get().getArrivedTime();
        return Optional.of(new CoverTarget(TARGET_SITE, cid, counts, arrived));
    }

    /**
     * 建立 Transfer#8(VIRTUAL#12) 的補蓋候選。
     */
    private Optional<CoverTarget> buildCoverTargetForTransfer8(GP7Context ctx) {
        Optional<Long> cT8Opt = locationTrackingRepository.findContainerOnTransfer(TRANSFER_ID);
        if (cT8Opt.isEmpty()) {
            return Optional.empty();
        }

        Long cid = cT8Opt.get();
        Counts counts = ctx.countsAt(cid);
        if (!isNeedy(counts)) {
            return Optional.empty();
        }

        if (!transferAtVirtual12()) {
            // 不在 V12，就算 needy 也不補（避免錯位）
            return Optional.empty();
        }

        Optional<LocationTracking> ltOpt = locationTrackingRepository.findByContainerMainId(cid);
        if (ltOpt.isEmpty() || ltOpt.get().getArrivedTime() == null) {
            //log.debug("[GP7] Transfer#8 container#{} 無 arrivedTime，跳過 FCFS 判斷。", cid);
            return Optional.empty();
        }

        LocalDateTime arrived = ltOpt.get().getArrivedTime();
        // 對 Gripper 來說目標站位名是 VIRTUAL#12
        return Optional.of(new CoverTarget(TARGET_VIRTUAL, cid, counts, arrived));
    }

    /**
     * FCFS + 驗證：
     * - 先從候選中挑 arrivedTime 最早的
     * - 但每個候選必須「驗證 PASS」才算有效候選
     * - refCoverId = 用來比對的 cover（Site#14 的 cover 或 手上 cover）
     */
    private Optional<CoverTarget> chooseCoverTargetByArrivalWithVerification(
            Long refCoverId,
            Optional<CoverTarget>... candidatesOpt
    ) {
        if (refCoverId == null) {
            return Optional.empty();
        }

        List<CoverTarget> passList = new ArrayList<>();
        for (Optional<CoverTarget> opt : candidatesOpt) {
            if (opt.isEmpty()) {
                continue;
            }
            CoverTarget t = opt.get();

            Long anchor = resolveAnchorCmId(t.containerId);
            Long refAnchor = resolveAnchorCmId(refCoverId);
            CoverOcrVerificationService.FinalDecision d = decideFinalOrWait(anchor, refAnchor, COVER_POOL);

            if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                passList.add(t);
            } else {
                // WAIT/BLOCK 都不列入
                //log.debug("[GP7] 候選 {} container#{} needy 但驗證非 PASS（decision={} anchor={} refCover={}）→ 排除",
//                        t.siteName, t.containerId, d, anchor, refCoverId);
            }
        }

        if (passList.isEmpty()) {
            return Optional.empty();
        }
        return passList.stream().min(Comparator.comparing(CoverTarget::arrivedTime));
    }

    /**
     * 「補蓋目標站」封裝資訊。
     */
    private static final class CoverTarget {
        final String siteName;           // Site#27 或 VIRTUAL#12
        final Long containerId;          // 目標 tray container
        final Counts counts;             // tray 當前層數
        final LocalDateTime arrivedTime; // FCFS 基準

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

// ====================== OCR#2 互鎖：是否佔用 Site#14 ======================

    private Integer readOcr2Level() {
        try {
            return plc.readInt16(PLC_DEVICE, OCR2_W_POS_LEVEL);
        } catch (Exception e) {
            log.warn("[GP7] 讀取 OCR#2 位置失敗：{}", e.getMessage());
            return null;
        }
    }

    private boolean isOcr2AtSite14() {
        Integer lv = readOcr2Level();
        boolean at = (lv != null && (lv == 0 || lv == LEVEL_SITE14));
        if (at) {
            //log.debug("[GP7] OCR#2 目前在 Site#14(Level={}) → 對 Site#14 的 MOVE/PICK/DROP 禁止。", lv);
        }
        return at;
    }

// ====================== Transfer#8 位置判斷 ======================

    private boolean transferAtVirtual12() {
        String name = "Transfer#" + TRANSFER_ID;
        TransferDeviceStatus ds = transferStatusCache.getLatest(name);
        boolean fresh = (ds != null) && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP7] 無法判定 {} 位置（快取無效），視為不可補。", name);
            return false;
        }
        Integer level = safeGetTransferLevel(ds);
        boolean at = (level != null && level == LEVEL_VIRTUAL_12);
        if (!at) {
            //log.debug("[GP7] {} 目前 Level={}，非 VIRTUAL#12({})。", name, level, LEVEL_VIRTUAL_12);
        }
        return at;
    }

// ====================== 平常待命 ======================

    private void maybeStandbyAtSite27(Long gripperId) {
        try {
            String gn = "Gripper#" + gripperId;
            GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);
            boolean fresh = (ds != null) && ds.isValidAndComplete(3);
            if (!fresh) return;

            boolean idle = "IDLE".equalsIgnoreCase(ds.getGripperStatus().getWorkingStatusText());
            Integer lv = safeGetLevel(ds);

            if (idle && (lv == null || lv != LEVEL_SITE27)) {
                if (!gripperBusy(gripperId)) {
                    log.info("[GP7] 無事可做，MOVE 到 {} 待命。", TARGET_SITE);
                    createMoveTo(gripperId, TARGET_SITE);
                }
            }
        } catch (Exception ignore) {
        }
    }

// ====================== 層數取得 + 保守推導 ======================

    private Counts countsAt(Long containerMainId, GP7Context ctx) {
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

// ====================== 建單封裝（含 layerCount 寫入） ======================

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

        log.info("[GP7] PICK 計畫: {} -> {}，預計搬 {} 層（蓋）；precount={} → leaveCount={}  " +
                        "(來源: 工蓋={} 上蓋={} 一般={}; 目標: 工蓋={} 上蓋={} 一般={}) | sourceId={}",
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
        log.info("[GP7] DROP 計畫: 夾爪 -> {}，預計放 {} 層（蓋）；layerCount(寫入)={} (= {} 工蓋 + {} 上蓋 + {} 一般)",
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
            log.warn("[GP7] 建立 {} 請求失敗：containerMainId={} tray_thickness_mm 解析失敗。", taskType, containerMainId);
            return Optional.empty();
        }
        req.setTargetHeightMm(BigDecimal.valueOf(trayThickness));

        boolean ok = requestRepository.save(req);
        if (ok) {
            log.info("[GP7] 建立 {} 請求成功: {} -> {} containerId={} layerCount(寫入)={}",
                    taskType, source, target, containerMainId, normalized);
            return Optional.of(req.getId());
        }
        log.warn("[GP7] 建立 {} 請求失敗: {} -> {}", taskType, source, target);
        return Optional.empty();
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
            log.info("[GP7] 建立 MOVE 請求 → {}", targetName);
            return Optional.of(req.getId());
        }
        log.warn("[GP7] 建立 MOVE 請求失敗 → {}", targetName);
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

// ====================== 共用：互斥 / 量測 / Level 取得 ======================

    private Long locationId(String name) {
        return GripperLocationCache.requireLocationId(locationPointRepository, name);
    }

    private class GP7Context {
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
            return countsByCid.computeIfAbsent(containerMainId, id -> GP7RequestGenerator.this.countsAt(id, this));
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

    private boolean gripperBusy(Long gripperId) {
        return requestRepository.existsUnfinishedRequestForDevice(gripperId)
                || taskRepository.existsUnfinishedTaskForGripper(gripperId);
    }

    private boolean infraredBusy(long infraredId) {
        return infraredRequestRepository.existsUnfinishedRequestForInfrared(infraredId)
                || infraredTaskRepository.existsUnfinishedTaskForInfrared(infraredId);
    }

    private boolean hasVerifiedQuantity(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                .orElse(0) > 0;
    }

    private void triggerInfraredMeasure(long infraredId, Long containerMainId) {
        if (infraredBusy(infraredId)) {
            return;
        }
        infraredRequestRepository.createMeasureRequestForContainer(containerMainId, infraredId);
    }

    private Integer safeGetLevel(GripperDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private Integer safeGetTransferLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

// ====================== OcrVerification：Anchor/Decision ======================

    /**
     * 取得 current 的祖先(Anchor) containerId：
     * - 優先用 LINEAGE_ROOT_CMID
     * - 否則沿 LINEAGE_PARENT_CMID 往上追（最多 8 層）
     * - 都找不到 → 回傳自己
     */
    private Long resolveAnchorCmId(Long selfCmId) {
        if (selfCmId == null) {
            return null;
        }

        Optional<Long> rootOpt = readLongAttr(selfCmId, ATTR_ROOT);
        if (rootOpt.isPresent()) {
            return rootOpt.get();
        }

        Long cur = selfCmId;
        Set<Long> visited = new HashSet<>();
        visited.add(cur);

        for (int i = 0; i < 8; i++) {
            Optional<Long> pOpt = readLongAttr(cur, ATTR_PARENT);
            if (pOpt.isEmpty()) {
                break;
            }

            Long p = pOpt.get();
            if (p == null || p <= 0) {
                break;
            }
            if (visited.contains(p)) {
                break;
            }

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
     * 取得 anchor 與 refCover 的驗證決策：
     * - 查不到或未終態：WAIT（保守，不推進）
     * - 終態：PASS/BLOCK（只回這兩種）
     */
    private CoverOcrVerificationService.FinalDecision decideFinalOrWait(Long anchorCmId, Long refCoverId, String refSite) {
        if (anchorCmId == null || refCoverId == null) return CoverOcrVerificationService.FinalDecision.WAIT;

        Optional<OcrVerification> ovOpt =
                ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorCmId, refCoverId);

        if (ovOpt.isEmpty()) {
            //log.debug("[GP7] {} 等待 OCR 流程啟動（anchor={} ref={}）→ WAIT", refSite, anchorCmId, refCoverId);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }

        CoverOcrVerificationService.FinalDecision d =
                ocrVerificationService.decideFinal(anchorCmId, ovOpt.get());

        if (d != CoverOcrVerificationService.FinalDecision.PASS
                && d != CoverOcrVerificationService.FinalDecision.BLOCK) {
            //log.debug("[GP7] {} 等待 OCR 驗證結果（anchor={} ref={} decision={}）→ WAIT", refSite, anchorCmId, refCoverId, d);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }
        return d;
    }

// ====================== 托盤厚度解析 ======================

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
