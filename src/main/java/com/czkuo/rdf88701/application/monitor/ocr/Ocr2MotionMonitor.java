package com.czkuo.rdf88701.application.monitor.ocr;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.cover.CoverLaneDecisionService;
import com.czkuo.rdf88701.application.service.cover.CoverLaneDecisionService.TrMode;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.common.enums.cover.CoverLane;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.OcrVerification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.czkuo.rdf88701.application.monitor.ocr.Ocr2Io.*;

/**
 * Ocr2MotionMonitor
 * -----------------------------------------------------------------------------
 * 只負責 OCR#2 的 PLC 動作控制（MOVE / COMP / 避讓 / 停車）。
 *
 * 核心職責：
 * 1) 依 TR4/TR5 的「供蓋 / 回收 路徑」判斷是否需要避讓（沿用 CoverLaneDecisionService）
 * 2) 當 OCR#2 不能卡在 pool（Site#12 / Site#14）時，自動讓位到對側 UP
 * 3) 平時（無需求）停在安全的 UP 停車位
 *
 * 不做的事：
 * - 不派 OCR 任務
 * - 不送 Collect
 * - 不介入 Result 流程（本支只把 OCR#2 移到位，等待 Result 端處理）
 *
 * 執行順序（重要：反映你要求的優先序）：
 * 0) compensate (MOVE/COMP 握手收斂)
 * 1) Collect 互鎖：Collect 中一律不動
 * 2) Lane 避讓：MAIN 回收 -> SUB 回收 -> MAIN 供蓋 -> SUB 供蓋
 * 3) Group 避讓：Group1(Site#12) -> Group2(Site#14)
 * 4) 正常服務：選站(12/14) -> MOVE 到 DOWN+Level
 * 5) 無目標：idleUp
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ocr2MotionMonitor {

    // === 祖先屬性鍵 ===
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT   = "LINEAGE_ROOT_CMID";

    private final PlcAccessService plc;

    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;

    private final GripperRequestRepository gripperRequestRepository;
    private final GripperTaskRepository gripperTaskRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final TransferTaskRepository transferTaskRepository;

    // OCR 驗證（同批判定）
    private final OcrVerificationRepository ocrVerificationRepository;
    private final CoverOcrVerificationService ocrVerificationService;

    private final GripperStatusCache gripperStatusCache;
    private final TransferStatusCache transferStatusCache;

    private final CoverLaneDecisionService coverLaneDecisionService;

    // ----------------------------
    // Motion session / state
    // ----------------------------
    private Long    currentContainerId = null;
    private Integer targetSiteLevel    = null;
    private String  targetSiteName     = null;

    private boolean loweringInProgress = false;
    private boolean raisingInProgress  = false;


    @Scheduled(fixedDelay = 400)
    public void monitor() {
        try {
            // 0) MOVE/COMP 補償（讓 PLC 先把交握收斂）
            if (compensate()) return;

            // 1) 若 Collect 正在交握（Result 端回報中）→ 暫停動作
            if (plc.readBoolean(DEVICE, B_COLLECT_REQ) || plc.readBoolean(DEVICE, B_COLLECT_ACK)) {
                return;
            }

            // 2) 避讓（讓位策略；只在非 Collect 時執行）

            // MAIN lane（13/14/TR5）(避讓使移載回待命點)
            if (tryYieldForTransferParking(TRANSFER_5)) return;
            // SUB lane（11/12/TR4 (避讓使移載回待命點)
            if (tryYieldForTransferParking(TRANSFER_4)) return;

            // MAIN lane（13/14/TR5）(回收優先)
            if (tryYieldRecallMain()) return;
            // SUB lane（11/12/TR4）(回收優先)
            if (tryYieldRecallSub())  return;

            // MAIN lane（13/14/TR5）(供給)
            if (tryYieldSupplyMain()) return;
            // SUB lane（11/12/TR4）(供給)
            if (tryYieldSupplySub())  return;

            // MAIN lane（13/14/TR5）(避讓使夾爪可取)
            if (tryYieldForPickMain()) return;
            // SUB lane（11/12/TR4）(避讓使夾爪可取)
            if (tryYieldForPickSub()) return;

            // 3) 目標站點選擇（需要 OCR + 互鎖允許）
            Target target = chooseBestTargetSite();
            if (target == null) {
                // 沒有可服務的站點 → 清 session + 停在上升位
                clearMotionSession("no-target");
                currentContainerId = null;
                targetSiteLevel = null;
                targetSiteName  = null;
                idleUp();
                return;
            }

            Long cmId = target.containerId();
            int  site = target.siteLevel();
            String siteName = target.siteName();

            // 切換站點/容器 → 清動作狀態
            if (!siteEquals(site, targetSiteLevel) || !cmEquals(cmId, currentContainerId)) {
                targetSiteLevel = site;
                targetSiteName  = siteName;
                currentContainerId = cmId;
                clearMotionSession("target-changed");
                log.info("[OCR2-Motion] 目標切換：{} cm#{}", siteName, cmId);
            }

            // 4) 待機 + Idle 才能下命令
            if (!deviceIdleAndStandby()) return;

            // 5) 尚未到目標位（Level!=site 或 Bay!=2）→ MOVE(DOWN, Level=site)
            if (!isAtDown() || !isAtLevel(site)) {
                if (!loweringInProgress) {
                    moveToSite(cmId, site);
                    loweringInProgress = true;
                }
                return;
            }

            // 6) 已到位（Bay=2 且 Level=site），動作端就不再做事，等待 Result 端處理
            //    （若之後沒需求，Result 端 Collect 完 → 本端下一輪會 idleUp）
        } catch (Exception e) {
            log.error("[OCR2-Motion] Monitor exception", e);
        }
    }

    // ========= 避讓與選站（沿用你原邏輯，可依需要再加） =========

    private Target pickIfAvailable(String siteName, int siteLevel, long transferId, long gripperId) {
        Optional<Long> cmOpt = locationTrackingRepository.findContainerAtLocationName(siteName);
        if (cmOpt.isEmpty()) return null;
        Long cmId = cmOpt.get();
        if (!needsOcr(cmId)) return null;
        if (!interlockAllows(siteLevel, transferId, gripperId)) return null;
        return new Target(siteName, siteLevel, cmId);
    }

    private Target chooseBestTargetSite() {
        Target t12 = pickIfAvailable(SITE12_NAME, SITE12_LEVEL, TRANSFER_4, GRIPPER_6);
        Target t14 = pickIfAvailable(SITE14_NAME, SITE14_LEVEL, TRANSFER_5, GRIPPER_7);
        if (t12 == null && t14 == null) return null;
        if (t12 != null && t14 == null) return t12;
        if (t12 == null) return t14;

        // 兩邊都有 → 選最近 Level
        int curLv = plc.readInt16(DEVICE, W_POS_LEVEL);
        int d12 = Math.abs(curLv - t12.siteLevel());
        int d14 = Math.abs(curLv - t14.siteLevel());
        return d12 <= d14 ? t12 : t14;
    }

    private boolean interlockAllows(int siteLevel, long transferId, long gripperId) {
        // repo 層：有未完成請求/任務 → 禁止
        if (gripperRequestRepository.existsUnfinishedRequestForDevice(gripperId)
                || gripperTaskRepository.existsUnfinishedTaskForGripper(gripperId)) {
            return false;
        }
        if (transferRequestRepository.existsUnfinishedRequestForDevice(transferId)
                || transferTaskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            return false;
        }

        // cache 層：Gripper/Transfer 均需 Idle（失效也擋）
        GripperDeviceStatus gds = gripperStatusCache.getLatest("Gripper#" + gripperId);
        TransferDeviceStatus tds = transferStatusCache.getLatest("Transfer#" + transferId);
        if (gds == null || !gds.isValidAndComplete(3)) return false;
        if (tds == null || !tds.isValidAndComplete(3)) return false;

        Integer gs = safeStatus(gds); // 1:Idle
        Integer ts = safeStatus(tds); // 1:Idle
        if (gs == null || gs != 1) return false;
        if (ts == null || ts != 1) return false;

        // Gripper 不在目標站
        Integer gl = safeGetLevel(gds);
        if (gl != null && gl == siteLevel) return false;

        // 如需也擋 Transfer 在該站，可啟用
        // Integer tl = safeGetLevel(tds);
        // if (tl != null && tl == siteLevel) return false;

        return true;
    }

    private boolean tryYieldForTransferParking(long transferId) {

        // 1) repo 層：Transfer 有未完成請求/任務 → 代表它正在被流程驅動，別用 OCR2 去干擾
        if (transferRequestRepository.existsUnfinishedRequestForDevice(transferId)
                || transferTaskRepository.existsUnfinishedTaskForTransfer(transferId)) {
            return false;
        }

        // 2) cache 層：需要可用的 level
        TransferDeviceStatus tds = transferStatusCache.getLatest("Transfer#" + transferId);
        if (tds == null || !tds.isValidAndComplete(3)) return false;

        Integer lv = safeGetLevel(tds);
        if (lv == null) return false;

        // 3) 不在 12/14 → 視為「需要回待命點」
        return lv != SITE12_LEVEL && lv != SITE14_LEVEL;
    }


    // =========================================================================
    // 避讓邏輯（SUB lane：pool=Site#12）
    // =========================================================================

    /**
     * SUB 回收路徑避讓：
     * - 路徑：12 → 11
     * - 條件：pool(Site#12) 或 transfer(TR4) 有產品
     * - 結論：OCR#2 不能卡在 Site#12
     */
    private boolean tryYieldRecallSub() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE12_LEVEL)) return false;

        TrMode mode = coverLaneDecisionService.resolveModeSub();
        if (mode != TrMode.RECALL) return false;

        boolean needYield =
                coverLaneDecisionService.shouldYieldOcrFromPoolForLane(CoverLane.SUB);

        if (!needYield) return false;

        if (!interlockAllows(SITE14_LEVEL, TRANSFER_5, GRIPPER_7)) return false;

        log.info("[OCR2-Motion] 🔁 Yield SUB-RECALL: leave Site#12 → Site#14(UP)");
        moveToLevelUp(SITE14_LEVEL, pickContextContainer(SITE12_NAME, TRANSFER_4));
        return true;
    }

    /**
     * SUB 供蓋路徑避讓：
     * - 路徑：11 → 12
     * - 條件：staging(Site#11) 或 transfer(TR4) 有產品
     * - 結論：OCR#2 不能卡在 Site#12
     */
    private boolean tryYieldSupplySub() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE12_LEVEL)) return false;

        TrMode mode = coverLaneDecisionService.resolveModeSub();
        if (mode != TrMode.SUPPLY) return false;

        boolean needYield =
                coverLaneDecisionService.shouldYieldOcrFromPoolForLane(CoverLane.SUB);

        if (!needYield) return false;

        if (!interlockAllows(SITE14_LEVEL, TRANSFER_5, GRIPPER_7)) return false;

        log.info("[OCR2-Motion] 🔁 Yield SUB-SUPPLY: leave Site#12 → Site#14(UP)");
        moveToLevelUp(SITE14_LEVEL, pickContextContainer(SITE11_NAME, TRANSFER_4));
        return true;
    }

    // =========================================================================
    // 避讓邏輯（MAIN lane：pool=Site#14）
    // =========================================================================

    /**
     * MAIN 回收路徑避讓：
     * - 路徑：14 → 13
     * - 條件：pool(Site#14) 或 transfer(TR5) 有產品
     * - 結論：OCR#2 不能卡在 Site#14
     */
    private boolean tryYieldRecallMain() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE14_LEVEL)) return false;

        TrMode mode = coverLaneDecisionService.resolveModeMain();
        if (mode != TrMode.RECALL) return false;

        boolean needYield =
                coverLaneDecisionService.shouldYieldOcrFromPoolForLane(CoverLane.MAIN);

        if (!needYield) return false;

        if (!interlockAllows(SITE12_LEVEL, TRANSFER_4, GRIPPER_6)) return false;

        log.info("[OCR2-Motion] 🔁 Yield MAIN-RECALL: leave Site#14 → Site#12(UP)");
        moveToLevelUp(SITE12_LEVEL, pickContextContainer(SITE14_NAME, TRANSFER_5));
        return true;
    }

    /**
     * MAIN 供蓋路徑避讓：
     * - 路徑：13 → 14
     * - 條件：staging(Site#13) 或 transfer(TR5) 有產品
     * - 結論：OCR#2 不能卡在 Site#14
     */
    private boolean tryYieldSupplyMain() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE14_LEVEL)) return false;

        TrMode mode = coverLaneDecisionService.resolveModeMain();
        if (mode != TrMode.SUPPLY) return false;

        boolean needYield =
                coverLaneDecisionService.shouldYieldOcrFromPoolForLane(CoverLane.MAIN);

        if (!needYield) return false;

        if (!interlockAllows(SITE12_LEVEL, TRANSFER_4, GRIPPER_6)) return false;

        log.info("[OCR2-Motion] 🔁 Yield MAIN-SUPPLY: leave Site#14 → Site#12(UP)");
        moveToLevelUp(SITE12_LEVEL, pickContextContainer(SITE13_NAME, TRANSFER_5));
        return true;
    }

    /** Site#12：若 (Site#26↔Site#12) 或 (Site#37↔Site#12) 的 ocr(1/2) 成對相同，
     *  且 Site#12 的 coverLayers==0(無上蓋)，且 OCR 正在 Site#12 → 讓位到 Site#14(UP)
     */
    private boolean tryYieldForPickSub() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE12_LEVEL)) return false;

        Optional<Long> cm12Opt = locationTrackingRepository.findContainerAtLocationName(SITE12_NAME);
        if (cm12Opt.isEmpty()) return false;

        Long refAnchorId = resolveAnchorCmId(cm12Opt.get());
        boolean shouldYield = false;
        String matchBy = null;

        Optional<Long> cm26Opt = locationTrackingRepository.findContainerAtLocationName(SITE26_NAME);
        if (cm26Opt.isPresent()) {
            Long anchorId = resolveAnchorCmId(cm26Opt.get());

            Optional<OcrVerification> ovOpt =
                    ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorId, refAnchorId);

            if (ovOpt.isPresent()) {
                CoverOcrVerificationService.FinalDecision d =
                        ocrVerificationService.decideFinal(anchorId, ovOpt.get());

                if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                    shouldYield = true;
                    matchBy = SITE26_NAME;
                }
            }
        }

        if (!shouldYield) {
            Optional<Long> cm37Opt = locationTrackingRepository.findContainerAtLocationName(SITE37_NAME);
            if (cm37Opt.isPresent()) {
                Long anchorId = resolveAnchorCmId(cm37Opt.get());

                Optional<OcrVerification> ovOpt =
                        ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorId, refAnchorId);

                if (ovOpt.isPresent()) {
                    CoverOcrVerificationService.FinalDecision d =
                            ocrVerificationService.decideFinal(anchorId, ovOpt.get());

                    if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                        shouldYield = true;
                        matchBy = SITE37_NAME;
                    }
                }
            }
        }

        if (!shouldYield) return false;

        if (!interlockAllows(SITE14_LEVEL, TRANSFER_5, GRIPPER_7)) {
            log.info("[OCR2-Motion] Yield@Site12 → Site#14 blocked by interlock (TR#5/GP#7).");
            return false;
        }
        if (isAtLevel(SITE14_LEVEL) && isAtUp()) {
            log.info("[OCR2-Motion] Yield@Site12 → 已停 Site#14(UP)");
            return true;
        }

        log.info("[OCR2-Motion] 🔁 Yield: leave Site#12 → park UP at Site#14 (match by {}, side coverLayers=0).", matchBy);
        moveToLevelUp(SITE14_LEVEL, cm12Opt.get());
        return true;
    }

    /** Site#14：若 (Site#27↔Site#14) 或 (TR#8 在 VIRTUAL#12 ↔ Site#14) 成對相同，
     *  且「對側(27 或 TR8)為無上蓋 coverLayers==0」，且 OCR 正在 Site#14 → 讓位到 Site#12(UP)
     */
    private boolean tryYieldForPickMain() {
        if (!deviceIdleAndStandby()) return false;
        if (!isAtLevel(SITE14_LEVEL)) return false;

        Optional<Long> cm14Opt = locationTrackingRepository.findContainerAtLocationName(SITE14_NAME);
        if (cm14Opt.isEmpty()) return false;

        Long refAnchorId = resolveAnchorCmId(cm14Opt.get());
        boolean shouldYield = false;
        String matchBy = null;

        Optional<Long> cm27Opt = locationTrackingRepository.findContainerAtLocationName(SITE27_NAME);
        if (cm27Opt.isPresent()) {
            Long anchorId = resolveAnchorCmId(cm27Opt.get());

            Optional<OcrVerification> ovOpt =
                    ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorId, refAnchorId);

            if (ovOpt.isPresent()) {
                CoverOcrVerificationService.FinalDecision d =
                        ocrVerificationService.decideFinal(anchorId, ovOpt.get());

                if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                    shouldYield = true;
                    matchBy = SITE27_NAME;
                }
            }
        }

        if (!shouldYield) {
            Optional<Long> tr8CmOpt = getTransferContainerIfAtLevel(TRANSFER_8, LEVEL_VIRTUAL_12);
            if (tr8CmOpt.isPresent()) {
                Long anchorId = resolveAnchorCmId(tr8CmOpt.get());

                Optional<OcrVerification> ovOpt =
                        ocrVerificationRepository.findLatestByContainerMainIdAndRefContainerId(anchorId, refAnchorId);

                if (ovOpt.isPresent()) {
                    CoverOcrVerificationService.FinalDecision d =
                            ocrVerificationService.decideFinal(anchorId, ovOpt.get());

                    if (d == CoverOcrVerificationService.FinalDecision.PASS) {
                        shouldYield = true;
                        matchBy = "TR8@V12";
                    }
                }
            }
        }

        if (!shouldYield) return false;

        if (!interlockAllows(SITE12_LEVEL, TRANSFER_4, GRIPPER_6)) {
            log.info("[OCR2-Motion] Yield@Site14 → Site#12 blocked by interlock (TR#4/GP#6).");
            return false;
        }
        if (isAtLevel(SITE12_LEVEL) && isAtUp()) {
            log.info("[OCR2-Motion] Yield@Site14 → 已停 Site#12(UP)");
            return true;
        }

        log.info("[OCR2-Motion] 🔁 Yield: leave Site#14 → park UP at Site#12 (match by {}, side coverLayers=0).", matchBy);
        moveToLevelUp(SITE12_LEVEL, cm14Opt.get());
        return true;
    }

    /** 若 transfer 在指定 level 且其上有容器，回傳該容器的 OCR pair；否則回 Optional.empty() */
    private Optional<OcrPair> getTransferOcrPairIfAtLevel(long transferId, int expectLevel) {
        TransferDeviceStatus ds = transferStatusCache.getLatest("Transfer#" + transferId);
        Integer lv = (ds != null && ds.isValidAndComplete(3)) ? safeGetLevel(ds) : null;
        if (lv == null || lv != expectLevel) return Optional.empty();

        return locationTrackingRepository.findContainerOnTransfer(transferId)
                .map(this::getOcrPair);
    }

    /** 取得 transfer 在指定 level 時，其上的 containerMainId（否則 empty） */
    private Optional<Long> getTransferContainerIfAtLevel(long transferId, int expectLevel) {
        TransferDeviceStatus ds = transferStatusCache.getLatest("Transfer#" + transferId);
        Integer lv = (ds != null && ds.isValidAndComplete(3)) ? safeGetLevel(ds) : null;
        if (lv == null || lv != expectLevel) return Optional.empty();
        return locationTrackingRepository.findContainerOnTransfer(transferId);
    }

    /** 取 OCR 兩欄（trim；空字串→null） */
    private OcrPair getOcrPair(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> new OcrPair(cd.getOcrText1(), cd.getOcrText2()))
                .orElse(new OcrPair(null, null));
    }

    /** 對齊比：1↔1、2↔2。若某欄兩邊都有值且不同 → false；否則只要任一對齊欄位相等 → true。 */
    private boolean equalsOcrAligned(OcrPair a, OcrPair b) {
        boolean can1 = a.has1() && b.has1();
        boolean can2 = a.has2() && b.has2();
        if (can1 && !a.t1.equalsIgnoreCase(b.t1)) return false;
        if (can2 && !a.t2.equalsIgnoreCase(b.t2)) return false;
        boolean match1 = can1 && a.t1.equalsIgnoreCase(b.t1);
        boolean match2 = can2 && a.t2.equalsIgnoreCase(b.t2);
        return match1 || match2;
    }

    // ====================== OcrVerification：Anchor/Decision ======================

    /**
     * 取得 current 的祖先(Anchor) containerId：
     * - 優先用 LINEAGE_ROOT_CMID
     * - 否則沿 LINEAGE_PARENT_CMID 往上追（最多 8 層）
     * - 都找不到 → 回傳自己
     */
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
                    try { return Optional.of(Long.parseLong(v.trim())); }
                    catch (Exception ignore) { return Optional.empty(); }
                });
    }

    // ========= MOVE/COMP 補償與移動 =========

    /** 三段握手補償（回收 CMD_REQ、對 COMP_REQ 送 COMP_ACK、復位 COMP_ACK） */
    private boolean compensate() {
        boolean cmdAck  = plc.readBoolean(DEVICE, B_CMD_ACK);
        boolean compReq = plc.readBoolean(DEVICE, B_COMP_REQ);
        boolean compAck = plc.readBoolean(DEVICE, B_COMP_ACK);
        int ret = plc.readInt16(DEVICE, W_MOVE_RETCODE);

        if (cmdAck) {
            if (plc.readBoolean(DEVICE, B_CMD_REQ)) plc.writeBoolean(DEVICE, B_CMD_REQ, false);
            return true;
        }
        if (compReq && !compAck) {
            switch (ret) {
                case 0x0100 -> log.info("[OCR2-Motion] MOVE success.");
                case 0x0800 -> log.warn("[OCR2-Motion] MOVE abort.");
                case 0x0F00 -> log.error("[OCR2-Motion] MOVE fail.");
                default     -> log.warn("[OCR2-Motion] MOVE ret=0x{}", Integer.toHexString(ret));
            }
            plc.writeBoolean(DEVICE, B_COMP_ACK, true);
            return true;
        }
        if (!compReq && compAck) {
            plc.writeBoolean(DEVICE, B_COMP_ACK, false);
            loweringInProgress = false;
            raisingInProgress  = false;
            return true;
        }
        return false;
    }

    /** 平常維持上升位（Bay=1） */
    private void idleUp() {
        if (!deviceIdleAndStandby()) return;

        int curBay = plc.readInt16(DEVICE, W_POS_BAY);
        // if (curBay == BAY_UP || raisingInProgress) return;
        if (raisingInProgress) return;

        boolean decide = false;
        Long cmId = currentContainerId;
        // int bank  = plc.readInt16(DEVICE, W_POS_BANK);
        int level = plc.readInt16(DEVICE, W_POS_LEVEL);
        /**
         * 讀不到 Level (=0) 時，不硬寫 14；
         * 改以 GP6/GP7 的當前 Level 來挑選安全停車位（僅在 Site#12 / Site#14 之間）。
         * 若兩邊都被佔用或狀態無效，保守處理：不動作，等待下一輪。
         */
        if (level == 0 || curBay != BAY_UP) {
            Integer safeLevel = chooseSafeLevelForIdleUp();
            if (safeLevel == null) {
                log.warn("[OCR2-Motion] IdleUp skipped: unknown Level and both Site#12/#14 occupied (or GP state invalid).");
                return;
            }
            level = safeLevel;
            decide = true;
        }

        if (!decide) {
            return;
        }

        int mm100 = (cmId != null) ? readThicknessMm100(cmId) : DEFAULT_THICK_MMx100;
        int qty   = (cmId != null) ? readEstimatedQty(cmId)  : DEFAULT_QTY_WHEN_EMPTY;

        plc.writeBoolean(DEVICE, B_READY, true);
        plc.writeInt32(DEVICE, W_NO, cmId != null ? cmId.intValue() : NO_FOR_EMPTY);
        plc.writeInt32(DEVICE, W_TYPE, packTypeAndQty(TYPE_MOVE, qty));
        plc.writeInt32(DEVICE, W_LOC1_H, mm100);
        // plc.writeInt32(DEVICE, W_LOC2_BANK, bank);
        plc.writeInt32(DEVICE, W_LOC3_BAY, BAY_UP);
        plc.writeInt32(DEVICE, W_LOC4_LEVEL, level);
        plc.writeBoolean(DEVICE, B_CMD_REQ, true);

        raisingInProgress = true;
        log.info("[OCR2-Motion] ⬆️ UP to idle. cm#{} h={} q={} level={}",
                (cmId == null ? "(none)" : cmId), mm100, qty, level);
    }

    /** 以 DB 參數下發 MOVE 到目標站點（Bay=2, Level=site） */
    private void moveToSite(Long cmId, int siteLevel) {
        int bank  = plc.readInt16(DEVICE, W_POS_BANK);
        int mm100 = (cmId != null) ? readThicknessMm100(cmId) : DEFAULT_THICK_MMx100;
        int qty   = (cmId != null) ? readEstimatedQty(cmId)  : DEFAULT_QTY_WHEN_EMPTY;

        plc.writeBoolean(DEVICE, B_READY, true);
        plc.writeInt32(DEVICE, W_NO, cmId != null ? cmId.intValue() : NO_FOR_EMPTY);
        plc.writeInt32(DEVICE, W_TYPE, packTypeAndQty(TYPE_MOVE, qty));
        plc.writeInt32(DEVICE, W_LOC1_H, mm100);
        plc.writeInt32(DEVICE, W_LOC2_BANK, bank);
        plc.writeInt32(DEVICE, W_LOC3_BAY, BAY_DOWN);
        plc.writeInt32(DEVICE, W_LOC4_LEVEL, siteLevel);
        plc.writeBoolean(DEVICE, B_CMD_REQ, true);

        log.info("[OCR2-Motion] ▶️ MOVE to site={} cm#{} h={} q={} bank={}", siteLevel, cmId, mm100, qty, bank);
    }

    /** 停在上升位（Bay=1）移動到指定 Level（僅讓位/停車用） */
    private void moveToLevelUp(int targetLevel, Long cmContextId) {
        int bank  = plc.readInt16(DEVICE, W_POS_BANK);
        int mm100 = (cmContextId != null) ? readThicknessMm100(cmContextId) : DEFAULT_THICK_MMx100;
        int qty   = (cmContextId != null) ? readEstimatedQty(cmContextId)  : DEFAULT_QTY_WHEN_EMPTY;

        plc.writeBoolean(DEVICE, B_READY, true);
        plc.writeInt32(DEVICE, W_NO, cmContextId != null ? cmContextId.intValue() : NO_FOR_EMPTY);
        plc.writeInt32(DEVICE, W_TYPE, packTypeAndQty(TYPE_MOVE, qty));
        plc.writeInt32(DEVICE, W_LOC1_H, mm100);
        plc.writeInt32(DEVICE, W_LOC2_BANK, bank);
        plc.writeInt32(DEVICE, W_LOC3_BAY, BAY_UP);           // ← 停在上升位
        plc.writeInt32(DEVICE, W_LOC4_LEVEL, targetLevel);    // ← 目的地 Level
        plc.writeBoolean(DEVICE, B_CMD_REQ, true);

        raisingInProgress = true;
        log.info("[OCR2-Motion] ▶️ MOVE (yield park UP) level={} cm#{} h={} q={} bank={}",
                targetLevel, cmContextId, mm100, qty, bank);
    }

    // ========= 判斷工具/DB =========

    private boolean deviceIdleAndStandby() {
        if (!plc.readBoolean(DEVICE, B_STANDBY)) return false;
        int s = readS(plc.readInt16(DEVICE, W_STATUS)); // 1 Idle / 2 Processing / 3 Complete
        return s == 1;
    }
    private boolean isAtDown()  { return plc.readInt16(DEVICE, W_POS_BAY) == BAY_DOWN; }
    private boolean isAtUp()    { return plc.readInt16(DEVICE, W_POS_BAY) == BAY_UP; }
    private boolean isAtLevel(int site) { return plc.readInt16(DEVICE, W_POS_LEVEL) == site; }
    /** 只在 coverLayers 明確為 0 時回 true；null/其他值都視為不確定或有上蓋 → 不觸發避讓 */
    private boolean isNoCover(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(ContainerData::getCoverLayers)
                .map(v -> v != null && v == 0)
                .orElse(false);
    }

    /** 避讓時挑一顆「有意義的 container」當 context（厚度/數量用） */
    private Long pickContextContainer(String siteName, long transferId) {
        return locationTrackingRepository.findContainerAtLocationName(siteName)
                .or(() -> locationTrackingRepository.findContainerOnTransfer(transferId))
                .orElse(null);
    }

    private int readS(int w13c3) { return w13c3 & 0xFF; }
    @SuppressWarnings("unused") private int readR(int w13c3) { return (w13c3 >> 8) & 0xFF; }

    private boolean needsOcr(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(d -> isBlank(d.getOcrText1()) && isBlank(d.getOcrText2()))
                .orElse(true);
    }

    private int readEstimatedQty(Long cmId) {
        int q = containerDataRepository.findByContainerMainId(cmId)
                .map(ContainerData::getEstimatedQuantity)
                .map(v -> v == null ? 0 : v)
                .orElse(0);
        if (q < 0) q = 0;
        if (q > 9999) q = 9999;
        return q;
    }

    private int readThicknessMm100(Long cmId) {
        for (String k : new String[]{"tray_thickness_mm"}) {
            Optional<ContainerAttr> a = containerAttrRepository.findOne(cmId, k);
            if (a.isPresent()) {
                Integer v = parseMmToMm100(a.get().getAttrValue());
                if (v != null && v > 0) return v;
            }
        }
        return DEFAULT_THICK_MMx100;
    }

    private Integer parseMmToMm100(String mmStr) {
        if (mmStr == null || mmStr.isBlank()) return null;
        try {
            BigDecimal mm = new BigDecimal(mmStr.trim());
            return mm.multiply(new BigDecimal("100")).setScale(0, BigDecimal.ROUND_HALF_UP).intValueExact();
        } catch (Exception e) { return null; }
    }

    private Integer safeGetLevel(GripperDeviceStatus ds)  { try { return ds.getLevel(); } catch (Throwable ignore) { return null; } }
    private Integer safeGetLevel(TransferDeviceStatus ds) { try { return ds.getLevel(); } catch (Throwable ignore) { return null; } }
    private Integer safeStatus(GripperDeviceStatus ds)    { try { return ds.getGripperStatus().getGripperStatus(); } catch (Throwable ignore) { return null; } }
    private Integer safeStatus(TransferDeviceStatus ds)   { try { return ds.getTransferStatus().getTransferStatus(); } catch (Throwable ignore) { return null; } }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private boolean siteEquals(Integer a, Integer b) { return a != null && b != null && a.intValue() == b.intValue(); }
    private boolean cmEquals(Long a, Long b) { return a != null && b != null && a.longValue() == b.longValue(); }

    private int packTypeAndQty(int typeDec, int qtyDec) { return (qtyDec << 8) | typeDec; }

    private void clearMotionSession(String reason) {
        loweringInProgress = false;
        raisingInProgress  = false;
        //log.debug("[OCR2-Motion] clear session: {}", reason);
    }

    /**
     * 讀不到自身 Level 時，依 GP6/GP7 的位置挑一個安全的 Level(12/14) 作為停車位；
     * 若兩個都被占用或狀態無效則回 null（呼叫端應暫停動作）。
     */
    private Integer chooseSafeLevelForIdleUp() {
        var gp6 = gripperStatusCache.getLatest("Gripper#" + GRIPPER_6);
        var gp7 = gripperStatusCache.getLatest("Gripper#" + GRIPPER_7);

        Integer l6 = (gp6 != null && gp6.isValidAndComplete(3)) ? safeGetLevel(gp6) : null;
        Integer l7 = (gp7 != null && gp7.isValidAndComplete(3)) ? safeGetLevel(gp7) : null;

        boolean block12 = l6 != null && (l6 == 0 || l6 == SITE12_LEVEL);
        boolean block14 = l7 != null && (l7 == 0 || l7 == SITE14_LEVEL);

        if (!block14) return SITE14_LEVEL;  // Site#14 安全 → 優先停這
        if (!block12) return SITE12_LEVEL;  // 否則停 Site#12
        return null;                        // 兩邊都不安全 → 不動
    }

    // ========= 內部型別 =========
    private record Target(String siteName, int siteLevel, Long containerId) {}

    /** 兩欄 OCR 值（皆已 trim；空字串→null） */
    private static final class OcrPair {
        final String t1; final String t2;
        OcrPair(String a, String b) { this.t1 = normalize(a); this.t2 = normalize(b); }
        boolean has1() { return t1 != null; }
        boolean has2() { return t2 != null; }
        boolean isBlank() { return !has1() && !has2(); }
    }
    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
