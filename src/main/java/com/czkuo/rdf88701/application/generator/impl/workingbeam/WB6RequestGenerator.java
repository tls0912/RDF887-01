package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WB6RequestGenerator
 * ----------------------------------------------------------------------------
 * 工作樑 WB6：只負責「同一條線」的推進：Site#36 → Site#37 → Transfer#8
 * <p>
 * 線別/平行站點概念（與 WB5 對映）：
 * - WB6 線：Site#36 / Site#37 / Transfer#8
 * - 另一線平行：Site#25 / Site#26 / Site#27
 * <p>
 * 平行關係（用於「補蓋 / 等蓋」的節奏控制）：
 * - Site#12、Site#26、Site#37：補蓋前段池（同批無蓋的對應位置）
 * - Site#14、Site#27、Transfer#8：補蓋後段池（同批等蓋後的目標線）
 * <p>
 * 補蓋來源（由 GP6/GP7 搬運）：
 * - 上蓋由 Site#12 / Site#14 供應
 * - Site#26 / Site#37 / Site#27 / Transfer#8 可能出現「無蓋待補」tray
 * - Transfer#8 僅在 VIRTUAL#12/13/14 區域內視為位置可信，否則不建單（安全保護）
 * <p>
 * 互斥：
 * - WB6 與 GP5@Site#36 互斥：避免 GP5 在 36 上搬運時，WB6 同時推 36 → 37
 * - Site#37 無蓋流程時，與 GP6@Site#37 互斥（保守）：避免 GP6 正在 pick/drop 時 WB6 推 37 → TR8
 * <p>
 * 主要決策：
 * - TR8 若位置不在 V12/V13/V14 → 視為位置丟失，不建單（安全）
 * - TR8 在 V12 且已有帳 → 阻擋（目標衝突）
 * - Site#36 的放行依 R029_COUNT 與同批 lot 是否「最後一批」決定
 * - Site#37：
 *   - 有蓋：TR8 Ready（無帳且在 V12）→ 可推進
 *   - 無蓋：走「無蓋流程」：
 *     1) GP6 與 37 互斥
 *     2) 以 Site#12 作為 ref，若同料號則必須 OCR 驗證終態（PASS/BLOCK），否則 WAIT → 不動
 *     3) 若 12 同批 PASS，預設視為「12 正在供應這批」→ 37 不推進（節奏控制）
 *        但若偵測到平行站 Site#26 同批無蓋且 arrived(26) < arrived(37)，啟用「26 優先模式」：
 *        允許 37 直接走 Site#14 capacity gating（不被「12 同批 PASS 需等待」阻擋）
 *     4) 最終仍需通過 Site#14 capacity gating（waiting < capacity）且 TR8 必須 Ready
 */
@Slf4j
@Component("WB6")
@RequiredArgsConstructor
public class WB6RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final GripperRequestRepository gripperRequestRepository;
    private final GripperTaskRepository gripperTaskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;
    private final DeviceProcessStateReader stateReader;

    // TR8 位置快取
    private final TransferStatusCache transferStatusCache;

    // R029 同批資訊
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerMainRepository containerMainRepository;
    private final RobotInR029LotRepository r029LotRepository;

    // OCR 驗證
    private final OcrVerificationRepository ocrVerificationRepository;
    private final CoverOcrVerificationService ocrVerificationService;

    private final WorkingBeamStatusCache workingBeamStatusCache;

    // === 站點 / 設備命名 ===
    private static final String SITE_36 = "Site#36";
    private static final String SITE_37 = "Site#37";
    private static final String SITE_26 = "Site#26"; // 平行線位（與 37 對映）
    private static final String SITE_12 = "Site#12";
    private static final String SITE_14 = "Site#14";
    private static final String SITE_27 = "Site#27";

    private static final long TRANSFER_8_ID = 8L;
    private static final String TRANSFER_8_NAME = "Transfer#8";

    private static final long GRIPPER_5_ID = 5L;
    private static final String GRIPPER_5_NAME = "Gripper#5";

    private static final long GRIPPER_6_ID = 6L;
    private static final String GRIPPER_6_NAME = "Gripper#6";

    // === TR8 位置名稱 / Level 對應（與 TR8 保持一致）===
    private static final String VIRTUAL_12_NAME = "VIRTUAL#12";
    private static final String VIRTUAL_13_NAME = "VIRTUAL#13";
    private static final String VIRTUAL_14_NAME = "VIRTUAL#14";
    private static final int LEVEL_V12 = 212;
    private static final int LEVEL_V13 = 213;
    private static final int LEVEL_V14 = 214;

    // === R029 屬性鍵 ===
    private static final String ATTR_R029_COUNT = "R029_COUNT";
    private static final String ATTR_R029_LOG_ID = "R029_LOG_ID";

    // === 祖先屬性鍵 ===
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT   = "LINEAGE_ROOT_CMID";

    // === 上蓋檢查旗標（TR8 走 12→13 前可用）===
    private static final String ATTR_NEED_COVER_CHECK = "NEED_COVER_CHECK";

    // 名稱長度上限（與 GripperTaskTransferService 保持一致）
    private static final int NAME_MAX = 20;

    // 供存 groups 的屬性鍵（可選）
    private static final String ATTR_GROUPS = "r029_groups";

    // 與 GripperTaskTransferService 一致的名稱解析規則
    private static final Pattern ID_PATTERN_STRICT_WITH_IDX =
            Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*(?:\\+[1-9][0-9]*)*)_([1-9][0-9]*)$");
    private static final Pattern ID_PATTERN_STRICT_BASE =
            Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*)$");

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        WB6Context local = new WB6Context();

        // 0) 互斥檢查：GP5@36 / 本 Beam
        if (gripperBusy(GRIPPER_5_ID, SITE_36)) {
            //log.debug("[WB6] {} 已有未完成請求或任務，略過", GRIPPER_5_NAME);
            return Optional.empty();
        }
        if (workingBeamBusy(workingBeamId)) {
            //log.debug("[WB6] Beam#{} 已有未完成請求或任務，略過", workingBeamId);
            return Optional.empty();
        }

        // 讀設備狀態
        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB6] 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }
        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB6] 設備狀態尚未準備好（非 transfer standby），此次不建請求");
            return Optional.empty();
        }

        // ─────────────────────────────────────────────────────────────
        // 蒐集快照
        // ─────────────────────────────────────────────────────────────

        // A) Site#36：必須有 R029_COUNT（嚴格），否則視為異常不建單
        Optional<Long> c36Opt = local.getContainerAtSite(SITE_36);
        boolean site36Has = c36Opt.isPresent();
        Long c36Id = c36Opt.orElse(null);

        int cover36 = 0;
        int product36 = 0;
        Integer count36 = null;

        if (site36Has) {
            Optional<ContainerData> d36 = local.getContainerData(c36Id);
            if (d36.isPresent()) {
                cover36 = safeInt(d36.get().getCoverLayers());
                product36 = safeInt(d36.get().getProductLayers());
            }
            count36 = readR029CountStrict(c36Id);
            if (count36 == null || count36 <= 0) {
                log.warn("[WB6] {} 缺少有效的 R029_COUNT（cm#{}），視為異常，不建單", SITE_36, c36Id);
            }
        }

        // B) Site#37：上蓋數（可能為 NULL=未知）
        Optional<Long> c37Opt = local.getContainerAtSite(SITE_37);
        boolean site37Has = c37Opt.isPresent();
        Long c37Id = c37Opt.orElse(null);

        Integer cover37Val = null; // null=未知
        if (site37Has) {
            cover37Val = getCoverLayersStrict(c37Id, local);
        }
        boolean cover37Known = (cover37Val != null);
        int cover37 = cover37Known ? Math.max(0, cover37Val) : 0;

        // C) Transfer#8 是否有帳（DB）
        boolean tr8Has = local.getContainerOnTransfer(TRANSFER_8_ID).isPresent();

        // D) Transfer#8 位置（Cache）－必須在 V12/V13/V14
        boolean tr8AtV12 = false;
        boolean tr8AtV13 = false;
        boolean tr8AtV14 = false;
        Integer tr8Level = null;

        TransferDeviceStatus tr8Ds = transferStatusCache.getLatest(TRANSFER_8_NAME);
        if (tr8Ds != null && tr8Ds.isValidAndComplete(3)) {
            tr8Level = safeGetLevel(tr8Ds);
            if (tr8Level != null) {
                tr8AtV12 = (tr8Level == LEVEL_V12);
                tr8AtV13 = (tr8Level == LEVEL_V13);
                tr8AtV14 = (tr8Level == LEVEL_V14);
            }
        }

        boolean tr8InAllowedArea = tr8AtV12 || tr8AtV13 || tr8AtV14;
        if (!tr8InAllowedArea) {
            log.warn("[WB6] {} 不在 {} / {} / {} 範圍內（level={}），視為實際位置丟失，為求安全本次不建單",
                    TRANSFER_8_NAME, VIRTUAL_12_NAME, VIRTUAL_13_NAME, VIRTUAL_14_NAME, tr8Level);
            return Optional.empty();
        }

        boolean tr8Ready = (!tr8Has) && tr8AtV12;

        //log.debug("[WB6] snapshot: 36.has={}, 36.product={}, 36.cover={}, 36.count={}, " +
//                        "37.has={}, 37.coverKnown={}, 37.cover={}, " +
//                        "TR8.has={}, TR8.level={}, TR8.atV12={}, TR8.atV13={}, TR8.atV14={}, TR8.ready={}",
//                site36Has, product36, cover36, count36,
//                site37Has, cover37Known, cover37,
//                tr8Has, tr8Level, tr8AtV12, tr8AtV13, tr8AtV14, tr8Ready);

        // Step 1：TR8 在 V12 且已有帳 → 阻擋（目標衝突）
        if (tr8AtV12 && tr8Has) {
            log.warn("[WB6] 阻擋：{} 在 {} 且已有帳，避免衝突，本次不建單", TRANSFER_8_NAME, VIRTUAL_12_NAME);
            return Optional.empty();
        }

        // ─────────────────────────────────────────────────────────────
        // Site#36 放行規則（R029）
        // 1) product36 > count36 → 不放行
        // 2) product36 == count36 → 放行（護欄：cover <= 1）
        // 3) product36 <  count36 → 僅「最後一批」才放行
        // ─────────────────────────────────────────────────────────────
        boolean cond36 = false;
        if (site36Has && count36 != null && count36 > 0) {
            boolean overTarget = (product36 > count36);
            boolean exactHit   = (product36 == count36);
            boolean lastBatch  = isLastBatchByR029(c36Id, local);

            if (overTarget) {
                cond36 = false;
                //log.debug("[WB6] {} 超標不放行：product={} > count={}", SITE_36, product36, count36);
            } else if (exactHit) {
                cond36 = (cover36 <= 1);
                //log.debug("[WB6] {} 達標{}放行：product=count={} 且 cover={}",
//                        SITE_36, cond36 ? "" : "但不", count36, cover36);
            } else {
                cond36 = lastBatch;
                //log.debug("[WB6] {} 未達標：product={} < count={}，lastBatch={} cover={} → {}",
//                        SITE_36, product36, count36, lastBatch, cover36, cond36 ? "放行" : "等待補件");
            }
        } else if (site36Has) {
            log.warn("[WB6] {} 存在，但 R029_COUNT 不可用（null/<=0）→ 不建單", SITE_36);
        }

        // ─────────────────────────────────────────────────────────────
        // Site#37 放行規則
        // - cover known & >=1：TR8 Ready → 可動
        // - cover known & ==0：走「無蓋流程」（OCR 驗證 + capacity gating）
        // - cover unknown：不可動（保守）
        // ─────────────────────────────────────────────────────────────
        boolean cond37 = false;
        if (site37Has) {
            if (cover37Known && cover37 >= 1) {
                cond37 = tr8Ready;
                //log.debug("[WB6] {} 有上蓋：TR8.ready={} → {}", SITE_37, tr8Ready, cond37 ? "可動" : "不可動");
            } else if (cover37Known && cover37 == 0) {
                cond37 = decide37WhenNoCover(c37Id, tr8Ready, local);
            } else {
                //log.debug("[WB6] {} cover_layers=UNKNOWN（未明確），不符合條件", SITE_37);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // AND gating：兩邊同時有帳 → cond36 && cond37
        // 單邊有帳 → 單邊成立即可
        // ─────────────────────────────────────────────────────────────
        if (site36Has && site37Has) {
            if (cond36 && cond37) {
                ensureSplitIndexNameIfNeeded(c36Id);
                markNeedCoverCheck(c37Id);
                log.info("[WB6] 36&37 同時有帳且條件皆成立 → 由 {} 建單（目標：{}）", SITE_36, TRANSFER_8_NAME);
                return createRequest(workingBeamId, SITE_36);
            }
            //log.debug("[WB6] 36&37 同時有帳但條件不一致 → 不建單（cond36={}, cond37={}）", cond36, cond37);
            return Optional.empty();
        }

        if (site36Has) {
            if (cond36) {
                ensureSplitIndexNameIfNeeded(c36Id);
                log.info("[WB6] 只有 {} 有帳且條件符合（count={}） → 建單", SITE_36, count36);
                return createRequest(workingBeamId, SITE_36);
            }
            //log.debug("[WB6] 只有 {} 有帳但條件不滿足（product={}, cover={}, count={}）→ 不建單",
//                    SITE_36, product36, cover36, count36);
            return Optional.empty();
        }

        if (site37Has) {
            if (cond37) {
                markNeedCoverCheck(c37Id);
                log.info("[WB6] 只有 {} 有帳且條件符合 → 建單（目標：{}）", SITE_37, TRANSFER_8_NAME);
                return createRequest(workingBeamId, SITE_37);
            }
            //log.debug("[WB6] 只有 {} 有帳但條件不滿足 → 不建單", SITE_37);
            return Optional.empty();
        }

        //log.debug("[WB6] 無來源帳務，略過建立請求");
        return Optional.empty();
    }

    /* =============================== Busy / Mutex =============================== */

    /** 指定夾爪是否忙碌（有未完成請求或任務） */
    private boolean gripperBusy(Long gripperId) {
        return gripperRequestRepository.existsUnfinishedRequestForDevice(gripperId)
                || gripperTaskRepository.existsUnfinishedTaskForGripper(gripperId);
    }

    /**
     * Gripper 忙碌判定（含站點維度）：
     * - device 有未完成 request
     * - 或對該站點存在未完成 PICK/DROP 任務
     */
    private boolean gripperBusy(Long gripperId, String siteName) {
        Long targetId = WorkingBeamLocationCache.findLocationId(locationPointRepository, siteName);
        if (targetId == null) {
            log.warn("[WB6] 找不到 {} 的點位 ID，保守視為 GP{}-PICK/DROP 存在 → 阻擋建單", siteName, gripperId);
            return true;
        }

        return gripperRequestRepository.existsUnfinishedRequestForDevice(gripperId)
                || gripperTaskRepository.existsUnfinishedTaskForGripperToTargetAndType(gripperId, targetId, "PICK")
                || gripperTaskRepository.existsUnfinishedTaskForGripperToTargetAndType(gripperId, targetId, "DROP");
    }

    /** 指定工作樑裝置是否忙碌（有未完成請求或任務） */
    private boolean workingBeamBusy(long workingBeamId) {
        return requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    /* =============================== No-cover decision =============================== */

    /**
     * Site#37 無上蓋（cover=0）時能否往前：
     *
     * 互斥層（避免物理衝突）：
     * - GP6 正在對 Site#37 進行 PICK/DROP（或已有 request）→ 不可動
     * - GP6 手上持有 container（但尚未反映成 request/task）→ 不可動（保守）
     *
     * 節奏層（用 OcrVerification 表示「同批」與「流程狀態」）：
     * 1) 若 Site#12 存在且同料號：
     *      - verification 尚未終態（WAIT）→ 不推進
     *      - verification PASS → 視為 12 正在供應此批 → 預設不推進（等待節奏）
     * 2) 若 12 同批 PASS，才可評估 Site#26 是否同批無蓋：
     *      - 26 同批 PASS 且 arrived(26) < arrived(37) → 啟用「26 優先模式」：
     *          直接走 Site#14 capacity gating（跳過「12 同批 PASS 需等待」的阻擋）
     * 3) 其他情況 → 走 Site#14 capacity gating（需 14 PASS 且 waiting < capacity 且 TR8 Ready）
     */
    private boolean decide37WhenNoCover(Long c37Id, boolean tr8Ready, WB6Context local) {

        // 互斥：避免 GP6 正在對 37 做 PICK/DROP 時 WB6 推 37
        if (gripperBusy(GRIPPER_6_ID, SITE_37)) {
            //log.debug("[WB6] {} 已有取/放請求或任務至 {} → 不可動", GRIPPER_6_NAME, SITE_37);
            return false;
        }

        Optional<Long> gp6Holding = local.getContainerOnGripper(GRIPPER_6_ID);
        if (gp6Holding.isPresent() && !gripperBusy(GRIPPER_6_ID)) {
            //log.debug("[WB6] {} 手上已有帳等待產生請求/任務 → 不可動", GRIPPER_6_NAME);
            return false;
        }

        Long anchor37 = resolveAnchorCmId(c37Id);
        String anchorPartNo = getTrayTypeByContainerId(anchor37, local);

        // Step 1：以 Site#12 作為 ref，若同料號則必須 verification 已終態（PASS/BLOCK），否則 WAIT → 不動
        boolean sameBatch12 = false;
        Optional<Long> c12Opt = local.getContainerAtSite(SITE_12);
        if (c12Opt.isPresent()) {
            Long c12 = c12Opt.get();
            boolean pn12 = strEq(getTrayTypeByContainerId(resolveAnchorCmId(c12), local), anchorPartNo);
            if (pn12) {
                CoverOcrVerificationService.FinalDecision d12 = decideFinalOrWait(anchor37, c12, SITE_12);
                if (d12 == CoverOcrVerificationService.FinalDecision.WAIT) return false;
                sameBatch12 = (d12 == CoverOcrVerificationService.FinalDecision.PASS);
            }
        }

        // Step 2：若 12 同批 PASS，再檢查 26 是否同批無蓋；若 26 比 37 更早到位，啟用「26 優先模式」
        boolean sameBatch26 = false;
        LocalDateTime arr26 = null;
        LocalDateTime arr37 = null;

//        if (sameBatch12) {
//            Optional<Long> c26Opt = locationTrackingRepository.findContainerAtLocationName(SITE_26);
//            if (c26Opt.isPresent()) {
//                Long c26 = c26Opt.get();
//
//                Integer cover26Val = getCoverLayersStrict(c26);
//                int cover26 = cover26Val == null ? 0 : Math.max(0, cover26Val);
//
//                if (cover26 == 0) {
//                    Long anchor26 = resolveAnchorCmId(c26);
//                    boolean pn26 = strEq(getTrayTypeByContainerId(anchor26), anchorPartNo);
//
//                    if (pn26) {
//                        // 用「26 與 12」的驗證結果判斷 26 是否同批（ref=12）
//                        CoverOcrVerificationService.FinalDecision d26 =
//                                decideFinalOrWait(anchor26, c12Opt.get(), SITE_12);
//
//                        if (d26 == CoverOcrVerificationService.FinalDecision.WAIT) return false;
//
//                        if (d26 == CoverOcrVerificationService.FinalDecision.PASS) {
//                            arr37 = findArrivedTimeByContainerId(c37Id);
//                            arr26 = findArrivedTimeByContainerId(c26);
//                            sameBatch26 = true;
//                        }
//                    }
//                }
//            }
//        }

        // Step 3：capacity gating（ref=Site#14）
        if (sameBatch26 && arr26 != null && arr37 != null && arr26.isBefore(arr37)) {
            boolean allow = canMoveBySite14Capacity(anchor37, tr8Ready, local);
            //log.debug("[WB6] 37 無蓋：啟用 26 優先模式（26較早到位），37 可動? {}", allow);
            return allow;
        }

        // 一般節奏：只要 12 同批 PASS，就視為「12 正在供應這批」→ 本策略：37 不推進（等待）
        if (sameBatch12) {
            return false;
        }

        // 其他情境：直接用 14 capacity 來判斷是否可再放行一顆「無蓋待補」
        return canMoveBySite14Capacity(anchor37, tr8Ready, local);
    }

    /* =============================== R029 helpers =============================== */

    /**
     * Site#36「最後一批」判定（基於 R029）
     * - 取得自身 alias_code
     * - 取得 R029_LOG_ID → 找同批 carrierIds
     * - 只要同批其他 carrier 仍在 tracking → 非最後一批
     * - 都不在 tracking → 最後一批
     */
    private boolean isLastBatchByR029(Long containerMainId, WB6Context local) {
        if (containerMainId == null) return false;

        String selfAlias = local.getContainerMain(containerMainId)
                .map(ContainerMain::getAliasCode)
                .orElse(null);
        if (isBlank(selfAlias)) {
            //log.debug("[WB6] isLastBatch 判斷：找不到自身 alias_code（cm#{}）→ 視為非最後一批", containerMainId);
            return false;
        }

        Long logId = containerAttrRepository.findOne(containerMainId, ATTR_R029_LOG_ID)
                .map(ContainerAttr::getAttrValue)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(v -> {
                    try { return Long.parseLong(v); } catch (Exception e) { return null; }
                })
                .orElse(null);

        if (logId == null) {
            //log.debug("[WB6] isLastBatch 判斷：缺 R029_LOG_ID（cm#{}）→ 視為非最後一批", containerMainId);
            return false;
        }

        List<String> lots = r029LotRepository.findCarrierIdsByLogId(logId);
        if (lots == null || lots.isEmpty()) {
            //log.debug("[WB6] isLastBatch 判斷：logId={} lot 清單為空 → 視為非最後一批", logId);
            return false;
        }

        for (String alias : lots) {
            if (isBlank(alias) || alias.equalsIgnoreCase(selfAlias)) continue;

            Long otherCmId = containerMainRepository.findByAliasCode(alias)
                    .map(ContainerMain::getId)
                    .orElse(null);

            if (otherCmId == null) continue; // 尚未建帳 → 視為不在 tracking

            if (locationTrackingRepository.findByContainerMainId(otherCmId).isPresent()) {
                return false; // 仍有同批 lot 在場 → 不是最後一批
            }
        }
        return true;
    }

    /**
     * 若名稱可解析且尚未帶 _k，則補上下一個 k（冪等）
     * - 目的：最後一批放行時，確保 alias_code 具備 splitIndex 以利追蹤/比對
     */
    private void ensureSplitIndexNameIfNeeded(Long containerMainId) {
        try {
            if (containerMainId == null) return;
            String name = containerMainRepository.findById(containerMainId)
                    .map(ContainerMain::getAliasCode).orElse(null);

            NameParts p = NameParts.parse(name);
            if (!p.isParsable()) {
                //log.debug("[WB6][FinalBatchRename] 名稱不可解析，略過 cm#{} raw='{}'", containerMainId, name);
                return;
            }
            if (p.splitIndex != null) return; // 已經有 _k，不重覆改

            Integer max = containerMainRepository.findMaxSplitIndexByBase(p.base);
            int nextK = (max == null ? 1 : Math.max(1, max + 1));

            String newName = p.base + "_" + nextK;
            if (newName.length() > NAME_MAX) newName = newName.substring(0, NAME_MAX);

            boolean ok = containerMainRepository.updateAliasCode(containerMainId, newName);
            if (ok) {
                log.info("[WB6][FinalBatchRename] cm#{} '{}' → '{}'", containerMainId, name, newName);

                if (p.groups != null && !p.groups.isEmpty()) {
                    String groups = joinGroups(p.groups);
                    ContainerAttr a = new ContainerAttr();
                    a.setContainerMainId(containerMainId);
                    a.setAttrKey(ATTR_GROUPS);
                    a.setAttrValue(groups);
                    try { containerAttrRepository.upsert(a); } catch (Exception ignore) {}
                }
            } else {
                log.warn("[WB6][FinalBatchRename] 更新名稱失敗 cm#{} '{}' → '{}'", containerMainId, name, newName);
            }
        } catch (Exception e) {
            log.warn("[WB6][FinalBatchRename] 發生例外 cm#{}: {}", containerMainId, e.getMessage(), e);
        }
    }

    private String joinGroups(SortedSet<Integer> set) {
        return set.stream().map(String::valueOf).collect(Collectors.joining("+"));
    }

    /* =============================== Request creation =============================== */

    /** 建立 WorkingBeam Request（來源：Site#36 或 Site#37；目標：TR8） */
    private Optional<Long> createRequest(Long workingBeamId, String sourceSiteName) {
        WorkingBeamRequest request = new WorkingBeamRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setWorkingBeamId(workingBeamId);
        request.setDirection("IN");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());

        boolean saved = requestRepository.save(request);
        if (saved) {
            log.info("[WB6] 建立 WorkingBeamRequest 成功: beam#{} from {} → {} reqId={}, key={}",
                    workingBeamId, sourceSiteName, TRANSFER_8_NAME, request.getId(), request.getRequestKey());
            return Optional.of(request.getId());
        } else {
            log.warn("[WB6] 建立 WorkingBeamRequest 失敗: beam#{} from {}", workingBeamId, sourceSiteName);
            return Optional.empty();
        }
    }

    /* =============================== Ancestor / Anchor =============================== */

    /**
     * 取得 current 的祖先(Anchor) containerId：
     * - 優先用 LINEAGE_ROOT_CMID
     * - 否則沿 LINEAGE_PARENT_CMID 往上追（最多 8 層，避免髒資料循環）
     * - 都找不到 → 回傳自己
     *
     * 目的：拆批/分身會產生新 cmId，但應共享同一個 OCR 驗證上下文（以 anchor 為主鍵）
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

    /* =============================== Cover verification / capacity =============================== */

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
            //log.debug("[WB6] {} 等待 OCR 流程啟動（anchor={} ref={}）→ WAIT", refSite, anchorCmId, refContainerId);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }

        CoverOcrVerificationService.FinalDecision d =
                ocrVerificationService.decideFinal(anchorCmId, ovOpt.get());

        if (d != CoverOcrVerificationService.FinalDecision.PASS
                && d != CoverOcrVerificationService.FinalDecision.BLOCK) {
            //log.debug("[WB6] {} 等待 OCR 驗證結果（anchor={} ref={} decision={}）→ WAIT", refSite, anchorCmId, refContainerId, d);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }
        return d;
    }

    /** Site#14 對「指定 ref container」的可用蓋容量（以 cover_layers 表示） */
    private int getCoverCapacityAtSite14(Long c14, WB6Context local) {
        ContainerData cd14 = local.getContainerData(c14).orElse(null);
        if (cd14 == null) return 0;
        return safeInt(cd14.getCoverLayers());
    }

    /** 判斷某 container 是否為「無蓋待補」：product>0 且 cover==0 */
    private boolean isWaitingForCoverByData(Long containerMainId, WB6Context local) {
        ContainerData cd = local.getContainerData(containerMainId).orElse(null);
        if (cd == null) return false;
        int product = safeInt(cd.getProductLayers());
        int cover = safeInt(cd.getCoverLayers());
        return product > 0 && cover == 0;
    }

    /**
     * 統計下游「同一個 ref@14」正在等蓋的數量：
     * - Site#27 / Transfer#8 上，若 product>0 且 cover==0 → 視為等蓋
     * - 並且其 anchor 對 refContainerIdAt14 的驗證為 PASS 才算同批 waiting
     */
    private int countDownstreamWaitingForSameRef(Long refContainerIdAt14, WB6Context local) {
        int waiting = 0;

        // Site#27
        Optional<Long> c27Opt = local.getContainerAtSite(SITE_27);
        if (c27Opt.isPresent()) {
            Long c27 = c27Opt.get();
            if (isWaitingForCoverByData(c27, local)) {
                Long a27 = resolveAnchorCmId(c27);
                CoverOcrVerificationService.FinalDecision d =
                        decideFinalOrWait(a27, refContainerIdAt14, SITE_14);
                if (d == CoverOcrVerificationService.FinalDecision.PASS) waiting++;
            }
        }

        // Transfer#8
        Optional<Long> cTr8Opt = local.getContainerOnTransfer(TRANSFER_8_ID);
        if (cTr8Opt.isPresent()) {
            Long c8 = cTr8Opt.get();
            if (isWaitingForCoverByData(c8, local)) {
                Long a8 = resolveAnchorCmId(c8);
                CoverOcrVerificationService.FinalDecision d =
                        decideFinalOrWait(a8, refContainerIdAt14, SITE_14);
                if (d == CoverOcrVerificationService.FinalDecision.PASS) waiting++;
            }
        }

        return waiting;
    }

    /**
     * Site#14 capacity gating（for TR8）：
     * - 必須：Site#14 有帳，且 anchor37 對 ref=Site#14 的驗證為 PASS（同批）
     * - allow 條件：tr8Ready && (waiting < capacity)
     */
    private boolean canMoveBySite14Capacity(Long anchor37, boolean tr8Ready, WB6Context local) {
        Optional<Long> c14Opt = local.getContainerAtSite(SITE_14);
        if (c14Opt.isEmpty()) {
            //log.debug("[WB6] 37 無蓋：{} 無帳 → 不可動", SITE_14);
            return false;
        }
        Long c14 = tr8Ready ? c14Opt.get() : c14Opt.get(); // 保持可讀性

        CoverOcrVerificationService.FinalDecision d14 = decideFinalOrWait(anchor37, c14, SITE_14);
        if (d14 == CoverOcrVerificationService.FinalDecision.WAIT) return false;
        if (d14 != CoverOcrVerificationService.FinalDecision.PASS) {
            //log.debug("[WB6] 37 無蓋：{} decision={}（非 PASS）→ 不可動", SITE_14, d14);
            return false;
        }

        int capacity = getCoverCapacityAtSite14(c14, local);
        int waiting  = countDownstreamWaitingForSameRef(c14, local);

        boolean allow = tr8Ready && (waiting < capacity);
        //log.debug("[WB6] 37 無蓋：Site14Ref={} capacity={} waiting={} tr8Ready={} → allow={}",
//                c14, capacity, waiting, tr8Ready, allow);
        return allow;
    }

    /* =============================== Small utilities =============================== */

    /** 標記：此 container 從 Site#37 來，TR8 走 12→13 前需要檢查上蓋 */
    private void markNeedCoverCheck(Long containerMainId) {
        if (containerMainId == null) return;
        try {
            ContainerAttr a = new ContainerAttr();
            a.setContainerMainId(containerMainId);
            a.setAttrKey(ATTR_NEED_COVER_CHECK);
            a.setAttrValue("Y");
            containerAttrRepository.upsert(a);
            //log.debug("[WB6] 設定 TR8 上蓋檢查旗標：cm#{}", containerMainId);
        } catch (Exception e) {
            log.warn("[WB6] 設定 TR8 上蓋檢查旗標失敗 cm#{}：{}", containerMainId, e.getMessage());
        }
    }

    /** 嚴格讀取 R029_COUNT；null 或 <=0 視為不可用 */
    private Integer readR029CountStrict(Long containerMainId) {
        try {
            return containerAttrRepository.findOne(containerMainId, ATTR_R029_COUNT)
                    .map(ContainerAttr::getAttrValue)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::valueOf)
                    .filter(v -> v > 0)
                    .orElse(null);
        } catch (Exception e) {
            log.warn("[WB6] 讀取 R029_COUNT 失敗 cm#{}：{}", containerMainId, e.getMessage());
            return null;
        }
    }

    /** 料號來自 container_main.part_no（必要） */
    private String getTrayTypeByContainerId(Long containerMainId, WB6Context local) {
        return local.getContainerMain(containerMainId)
                .map(ContainerMain::getPartNo)
                .filter(this::notBlank)
                .orElse(null);
    }

    private String getTrayTypeByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getPartNo)
                .filter(this::notBlank)
                .orElse(null);
    }

    /** 嚴格：僅回傳 container_data.cover_layers（可能為 NULL=未知） */
    private Integer getCoverLayersStrict(Long containerMainId, WB6Context local) {
        if (containerMainId == null) return null;
        return local.getContainerData(containerMainId)
                .map(ContainerData::getCoverLayers)
                .orElse(null);
    }

    private Integer getCoverLayersStrict(Long containerMainId) {
        if (containerMainId == null) return null;
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        return (cd == null) ? null : cd.getCoverLayers();
    }

    /** 由 containerMainId 反查 location_tracking.arrived_time（若無 tracking 則回 null） */
    private LocalDateTime findArrivedTimeByContainerId(Long containerMainId) {
        if (containerMainId == null) return null;
        return locationTrackingRepository.findByContainerMainId(containerMainId)
                .map(LocationTracking::getArrivedTime)
                .orElse(null);
    }

    /** 取目前 Level；若 TransferDeviceStatus 欄位名稱不同，改這裡即可 */
    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try { return ds.getLevel(); } catch (Throwable ignore) { return null; }
    }

    private String safeTrim(String s) { return s == null ? "" : s.trim(); }
    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** null/空白 安全大小寫不敏感比較 */
    private boolean strEq(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    private static int safeInt(Integer v) { return v == null ? 0 : v; }

    // ======================= Name parse =======================

    /** 名稱解析結果（最小可用） */
    private static final class NameParts {
        final String raw;
        final String head;
        final String tag;
        final SortedSet<Integer> groups;
        final Integer splitIndex; // 可能為 null（若原名沒有 _idx）
        final String base;        // <head>_<tag>_<groups>

        private NameParts(String raw, String head, String tag, SortedSet<Integer> groups, Integer splitIndex) {
            this.raw = raw;
            this.head = head;
            this.tag = tag;
            this.groups = groups;
            this.splitIndex = splitIndex;

            if (groups != null) {
                this.base = head + "_" + tag + "_" + groups.stream().map(String::valueOf).collect(Collectors.joining("+"));
            } else {
                this.base = head;
            }
        }

        static NameParts parse(String s) {
            if (s == null) return new NameParts(null, null, null, null, null);

            Matcher m1 = ID_PATTERN_STRICT_WITH_IDX.matcher(s);
            if (m1.matches()) {
                String head = m1.group(1);
                String tag  = m1.group(2);
                String gs   = m1.group(3);
                Integer idx = Integer.parseInt(m1.group(4));
                SortedSet<Integer> groups = Arrays.stream(gs.split("\\+"))
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(TreeSet::new));
                return new NameParts(s, head, tag, groups, idx);
            }

            Matcher m0 = ID_PATTERN_STRICT_BASE.matcher(s);
            if (m0.matches()) {
                String head = m0.group(1);
                String tag  = m0.group(2);
                String gs   = m0.group(3);
                SortedSet<Integer> groups = Arrays.stream(gs.split("\\+"))
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(TreeSet::new));
                return new NameParts(s, head, tag, groups, null);
            }

            return new NameParts(s, null, null, null, null);
        }

        boolean isParsable() {
            return head != null && tag != null && groups != null && !groups.isEmpty();
        }
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }

    private class WB6Context {
        private final Map<String, Optional<Long>> containerBySite = new HashMap<>();
        private final Map<Long, Optional<Long>> containerByTransfer = new HashMap<>();
        private final Map<Long, Optional<Long>> containerByGripper = new HashMap<>();
        private final Map<Long, Optional<ContainerData>> containerDataById = new HashMap<>();
        private final Map<Long, Optional<ContainerMain>> containerMainById = new HashMap<>();
        private final Map<Long, Optional<LocationTracking>> trackingByContainerId = new HashMap<>();

        Optional<Long> getContainerAtSite(String siteName) {
            return containerBySite.computeIfAbsent(siteName, locationTrackingRepository::findContainerAtLocationName);
        }

        Optional<Long> getContainerOnTransfer(Long transferId) {
            return containerByTransfer.computeIfAbsent(transferId, locationTrackingRepository::findContainerOnTransfer);
        }

        Optional<Long> getContainerOnGripper(Long gripperId) {
            return containerByGripper.computeIfAbsent(gripperId, locationTrackingRepository::findContainerOnGripper);
        }

        Optional<ContainerData> getContainerData(Long containerMainId) {
            if (containerMainId == null) return Optional.empty();
            return containerDataById.computeIfAbsent(containerMainId, containerDataRepository::findByContainerMainId);
        }

        Optional<ContainerMain> getContainerMain(Long containerMainId) {
            if (containerMainId == null) return Optional.empty();
            return containerMainById.computeIfAbsent(containerMainId, containerMainRepository::findById);
        }

        Optional<LocationTracking> getTrackingByContainerId(Long containerMainId) {
            if (containerMainId == null) return Optional.empty();
            return trackingByContainerId.computeIfAbsent(containerMainId, locationTrackingRepository::findByContainerMainId);
        }
    }
}
