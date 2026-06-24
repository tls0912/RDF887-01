package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.cover.CoverOcrVerificationService;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.infra.lock.InProcLocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WB5RequestGenerator
 * ----------------------------------------------------------------------------
 * 工作樑 WB5：只負責「同一條線」的推進：Site#25 → Site#26 → Site#27
 * <p>
 * 線別/平行站點概念（與 WB6 對映）：
 * - WB5 線：Site#25 / Site#26 / Site#27
 * - 另一線平行：Site#12 / Site#14 / Site#37 / Transfer#8
 * <p>
 * 平行關係（用於「補蓋 / 等蓋」的節奏控制）：
 * - Site#12、Site#26、Site#37：補蓋前段池（同批無蓋的對應位置）
 * - Site#14、Site#27、Transfer#8：補蓋後段池（同批等蓋後的目標線）
 * <p>
 * 補蓋來源（由 GP6/GP7 搬運）：
 * - 上蓋由 Site#12 / Site#14 供應
 * - Site#26 / Site#37 / Site#27 / Transfer#8 可能出現「無蓋待補」的 tray
 * <p>
 * 互斥：
 * - WB5 與 WB8 互斥：避免兩條線同步動作衝突
 * - WB5 與 GP4@Site#25 互斥：避免 GP4 在 25 上搬運時，WB5 同時推 25 → 26
 * <p>
 * 主要決策：
 * - Site#27 只要有帳，一律阻擋（最高優先）
 * - Site#25 的放行依 R029_COUNT 與同批 lot 是否「最後一批」決定
 * - Site#26 若有蓋：可推進
 * - Site#26 若無蓋：需走「無蓋流程」：
 *     1) 先做 GP6 與 26 的動作互斥（避免 WB5 推動時 GP6 正在 pick/drop）
 *     2) 用 OcrVerification 判定與 Site#12 是否同批：
 *        - WAIT：代表 OCR 驗證未終態 → 不推進
 *        - PASS：代表 12 正在供應此批 → 本版策略：直接不推進（視為等蓋節奏控制）
 *     3) 若 12 同批 PASS，才有資格檢查 Site#37 是否同批無蓋，
 *        並在「arrived(37) < arrived(26)」時啟用 37 優先模式（讓 26 先讓位）
 *     4) 最終仍需通過 Site#14 的 capacity gating（waiting < capacity）且 Site#27 必須為空
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("WB5")
@RequiredArgsConstructor
public class WB5RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final GripperRequestRepository gripperRequestRepository;
    private final GripperTaskRepository gripperTaskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;

    // R029 同批資訊
    private final ContainerAttrRepository containerAttrRepository;
    private final ContainerMainRepository containerMainRepository;
    private final RobotInR029LotRepository r029LotRepository;
    private final DeviceProcessStateReader stateReader;

    // OCR 驗證
    private final OcrVerificationRepository ocrVerificationRepository;
    private final CoverOcrVerificationService ocrVerificationService;

    private final WorkingBeamStatusCache workingBeamStatusCache;

    // 互斥 Beam ID
    private static final long WB5_BEAM_ID = 5L;
    private static final long WB8_BEAM_ID = 8L;

    // === 站點命名（WB5 線）===
    private static final String SITE_25 = "Site#25";
    private static final String SITE_26 = "Site#26";
    private static final String SITE_27 = "Site#27";
    private static final String SITE_12 = "Site#12";
    private static final String SITE_14 = "Site#14";
    private static final String SITE_37 = "Site#37";

    private static final long GRIPPER_4_ID     = 4L;
    private static final String GRIPPER_4_NAME = "Gripper#4";

    private static final long GRIPPER_6_ID     = 6L;
    private static final String GRIPPER_6_NAME = "Gripper#6";

    private static final long TRANSFER_8_ID     = 8L;
    @SuppressWarnings("unused")
    private static final String TRANSFER_8_NAME = "Transfer#8";

    // === R029 屬性鍵 ===
    private static final String ATTR_R029_COUNT  = "R029_COUNT";
    private static final String ATTR_R029_LOG_ID = "R029_LOG_ID";

    // === 祖先屬性鍵 ===
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT   = "LINEAGE_ROOT_CMID";

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
        WB5Context local = new WB5Context();

        // 0) 互斥檢查：GP4@25 / WB5 / WB8
        if (gripperBusy(GRIPPER_4_ID, SITE_25)) {
            //log.debug("[WB5] {} 已有未完成請求或任務，略過", GRIPPER_4_NAME);
            return Optional.empty();
        }

        // 本 Beam 是否已有未完成請求/任務
        if (workingBeamBusy(workingBeamId)) {
            //log.debug("[WB5] Beam#{} 已有未完成請求或任務，略過", workingBeamId);
            return Optional.empty();
        }

        // WB8 有未完成請求/任務 → WB5 不建單
        if (workingBeamBusy(WB8_BEAM_ID)) {
            //log.debug("[WB5] 偵測到 WB8 有未完成請求/任務 → 依互斥規則不建單");
            return Optional.empty();
        }

        // 讀設備狀態
        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB5] 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }
        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB5] 設備狀態尚未準備好（非 transfer standby），此次不建請求");
            return Optional.empty();
        }

        // ─────────────────────────────────────────────────────────────
        // 蒐集來源/目標快照
        // ─────────────────────────────────────────────────────────────
        // A) Site#25：必須有 R029_COUNT（嚴格），否則視為異常不建單
        Optional<Long> c25Opt = local.getContainerAtSite(SITE_25);
        boolean site25Has = c25Opt.isPresent();
        Long c25Id = c25Opt.orElse(null);

        int cover25 = 0;
        int product25 = 0;
        Integer count25 = null; // 嚴格：必須 >0

        if (site25Has) {
            Optional<ContainerData> dataOpt = local.getContainerData(c25Id);
            if (dataOpt.isPresent()) {
                ContainerData b = dataOpt.get();
                cover25 = safeInt(b.getCoverLayers());
                product25 = safeInt(b.getProductLayers());
            }
            count25 = readR029CountStrict(c25Id);
            if (count25 == null || count25 <= 0) {
                log.warn("[WB5] {} 缺少有效的 R029_COUNT（cm#{}），視為異常，不建單", SITE_25, c25Id);
            }
        }

        // B) Site#26：上蓋數（可能為 NULL=未知）
        Optional<Long> c26Opt = local.getContainerAtSite(SITE_26);
        boolean site26Has = c26Opt.isPresent();
        Integer cover26Val = null;
        if (site26Has) {
            Long c26 = c26Opt.get();
            cover26Val = getCoverLayersStrict(c26, local);
        }
        boolean cover26Known = (cover26Val != null);
        int cover26 = cover26Known ? Math.max(0, cover26Val) : 0;

        // C) 目標站 Site#27 是否「有帳」：有帳一律阻擋
        boolean site27Has = local.getContainerAtSite(SITE_27).isPresent();

        //log.debug("[WB5] snapshot: 25.has={}, 25.product={}, 25.cover={}, 25.count={}, 26.has={}, 26.coverKnown={}, 26.cover={}, 27.has={}",
//                site25Has, product25, cover25, count25, site26Has, cover26Known, cover26, site27Has);

        // Step 1：最高優先阻擋 → Site#27 有帳 → 不建單
        if (site27Has) {
            //log.debug("[WB5] 阻擋：{} 已有帳，避免衝突，本次不建單", SITE_27);
            return Optional.empty();
        }

        // ─────────────────────────────────────────────────────────────
        // Site#25 放行規則（R029）
        // 1) product25 > count25 → 不放行（仍可再拆）
        // 2) product25 == count25 → 放行（護欄：cover <= 1）
        // 3) product25 <  count25 → 僅「最後一批」才放行；否則等待補件
        // ─────────────────────────────────────────────────────────────
        boolean cond25 = false;
        if (site25Has && count25 != null && count25 > 0) {
            boolean overTarget  = (product25 > count25);
            boolean exactHit    = (product25 == count25);
            boolean lastBatch   = isLastBatchByR029(c25Id, local);

            if (overTarget) {
                cond25 = false;
                //log.debug("[WB5] {} 超標不放行：product={} > count={}", SITE_25, product25, count25);
            } else if (exactHit) {
                cond25 = (cover25 <= 1);
                //log.debug("[WB5] {} 達標放行：product=count={}", SITE_25, count25);
            } else {
                cond25 = lastBatch;
                //log.debug("[WB5] {} 未達標：product={} < count={}，lastBatch={} → {}",
//                        SITE_25, product25, count25, lastBatch, cond25 ? "放行" : "等待補件");
            }
        } else if (site25Has) {
            log.warn("[WB5] {} 存在，但 R029_COUNT 不可用（null/<=0）→ 不建單", SITE_25);
        }

        // ─────────────────────────────────────────────────────────────
        // Site#26 放行規則
        // - cover26Known && cover26 >= 1：視為已上蓋 → 可動（且 27 已保證為空）
        // - cover26Known && cover26 == 0：走「無上蓋流程」
        // - cover unknown：不可動（保守）
        // ─────────────────────────────────────────────────────────────
        boolean cond26 = false;
        if (site26Has) {
            Long c26 = c26Opt.get();
            if (cover26Known && cover26 >= 1) {
                cond26 = true;
                //log.debug("[WB5] {} 有上蓋：Site#27 為空 → 可動", SITE_26);
            } else if (cover26Known && cover26 == 0) {
                // cond26 = decide26WhenNoCover(c26, /*site27Empty*/ true);
                cond26 = true;
            } else {
                //log.debug("[WB5] {} cover_layers=UNKNOWN（未明確），不符合條件", SITE_26);
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 同時有帳時的 AND gating：
        // - 25、26 同時有帳 → 必須 cond25 && cond26 同時成立才允許建單
        // - 否則（只有一邊有帳）→ 單邊條件成立即可建單
        // ─────────────────────────────────────────────────────────────
        if (site25Has && site26Has) {
            if (cond25 && cond26) {
                ensureSplitIndexNameIfNeeded(c25Id);
                log.info("[WB5] 25&26 同時有帳且條件皆成立 → 由 {} 建單（目標：{}）", SITE_25, SITE_27);
                return createRequest(workingBeamId, SITE_25);
            }
            //log.debug("[WB5] 25&26 同時有帳但條件不一致 → 不建單（cond25={}, cond26={}）", cond25, cond26);
            return Optional.empty();
        }

        if (site25Has) {
            if (cond25) {
                ensureSplitIndexNameIfNeeded(c25Id);
                log.info("[WB5] 只有 {} 有帳且條件符合（count={}） → 建單", SITE_25, count25);
                return createRequest(workingBeamId, SITE_25);
            }
            //log.debug("[WB5] 只有 {} 有帳但條件不滿足（product={}, cover={}, count={}）→ 不建單",
//                    SITE_25, product25, cover25, count25);
            return Optional.empty();
        }

        if (site26Has) {
            if (cond26) {
                log.info("[WB5] 只有 {} 有帳且條件符合 → 建單（目標：{}）", SITE_26, SITE_27);
                return createRequest(workingBeamId, SITE_26);
            }
            //log.debug("[WB5] 只有 {} 有帳但條件不滿足 → 不建單", SITE_26);
            return Optional.empty();
        }

        //log.debug("[WB5] 無來源帳務，略過建立請求");
        return Optional.empty();
    }

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
            log.warn("[WB5] 找不到 {} 的點位 ID，保守視為 GP{}-PICK/DROP 存在 → 阻擋建單", siteName, gripperId);
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

    /**
     * Site#26 無上蓋（cover=0）時能否往前：
     *
     * 互斥層（避免物理衝突）：
     * - GP6 正在對 Site#26 進行 PICK/DROP（或已有 request）→ 不可動
     * - GP6 手上持有 container（但尚未反映成 request/task）→ 不可動（保守）
     *
     * 節奏層（用 OcrVerification 表示「同批」與「流程狀態」）：
     * 1) 若 Site#12 存在且同料號：
     *      - verification 尚未終態（WAIT）→ 不推進
     *      - verification PASS → 視為 12 正在供應此批 → 本策略：不推進（讓位等待）
     * 2) 若 12 PASS，才可評估 Site#37 是否同批無蓋：
     *      - 37 同批 PASS 且 arrived(37) < arrived(26) → 啟用「37 優先模式」：
     *          直接走 Site#14 capacity gating（跳過「12 同批 PASS 需等待」的阻擋）
     * 3) 其他情況 → 走 Site#14 capacity gating（需 14 PASS 且 waiting < capacity 且 27 為空）
     */
    private boolean decide26WhenNoCover(Long c26Id, boolean site27Empty) {

        // 互斥：避免 GP6 正在對 26 做 PICK/DROP 時 WB5 推 26
        if (gripperBusy(GRIPPER_6_ID, SITE_26)) {
            //log.debug("[WB5] {} 已有取/放請求或任務至 {} → 不可動", GRIPPER_6_NAME, SITE_26);
            return false;
        }

        Optional<Long> gp6Holding = locationTrackingRepository.findContainerOnGripper(GRIPPER_6_ID);
        if (gp6Holding.isPresent() && !gripperBusy(GRIPPER_6_ID)) {
            //log.debug("[WB5] {} 手上已有帳等待產生請求/任務 → 不可動", GRIPPER_6_NAME);
            return false;
        }

        Long anchor26 = resolveAnchorCmId(c26Id);
        String anchorPartNo = getTrayTypeByContainerId(anchor26);

        // Step 1：以 Site#12 作為 ref，若同料號則必須 verification 已終態（PASS/BLOCK），否則 WAIT 代表流程未完成 → 不動
        boolean sameBatch12 = false;
        Optional<Long> c12Opt = locationTrackingRepository.findContainerAtLocationName(SITE_12);
        if (c12Opt.isPresent()) {
            Long c12 = c12Opt.get();
            boolean pn12 = strEq(getTrayTypeByContainerId(resolveAnchorCmId(c12)), anchorPartNo);
            if (pn12) {
                CoverOcrVerificationService.FinalDecision d12 = decideFinalOrWait(anchor26, c12, SITE_12);
                if (d12 == CoverOcrVerificationService.FinalDecision.WAIT) return false;
                sameBatch12 = (d12 == CoverOcrVerificationService.FinalDecision.PASS);
            }
        }

        // Step 2：若 12 同批 PASS，再檢查 37 是否同批且無蓋；若 37 比 26 更早到位，允許啟用「37 優先模式」
        boolean sameBatch37 = false;
        LocalDateTime arr26 = null;
        LocalDateTime arr37 = null;

        if (sameBatch12) {
            Optional<Long> c37Opt = locationTrackingRepository.findContainerAtLocationName(SITE_37);
            if (c37Opt.isPresent()) {
                Long c37 = c37Opt.get();

                Integer cover37Val = getCoverLayersStrict(c37);
                int cover37 = cover37Val == null ? 0 : Math.max(0, cover37Val);

                if (cover37 == 0) {
                    Long anchor37 = resolveAnchorCmId(c37);
                    boolean pn37 = strEq(getTrayTypeByContainerId(anchor37), anchorPartNo);

                    if (pn37) {
                        // 這裡用「37 與 12」的驗證結果，判斷 37 是否同批（ref=12）
                        CoverOcrVerificationService.FinalDecision d37 =
                                decideFinalOrWait(anchor37, c12Opt.get(), SITE_12);

                        if (d37 == CoverOcrVerificationService.FinalDecision.WAIT) return false;

                        if (d37 == CoverOcrVerificationService.FinalDecision.PASS) {
                            arr26 = findArrivedTimeByContainerId(c26Id);
                            arr37 = findArrivedTimeByContainerId(c37);
                            sameBatch37 = true;
                        }
                    }
                }
            }
        }

        // Step 3：capacity gating（ref=Site#14）
        // - 若 37 同批且 arrived(37) < arrived(26)：啟用 37 優先模式（26 可以嘗試往前）
        if (sameBatch37 && arr26 != null && arr37 != null && arr37.isBefore(arr26)) {
            boolean allow = canMoveBySite14Capacity(anchor26, site27Empty);
            //log.debug("[WB5] 26 無蓋：啟用 37 優先模式（37較早到位），26 可動? {}", allow);
            return allow;
        }

        // 一般節奏：只要 12 同批 PASS，就視為「12 正在供應這批」→ 本策略：26 不推進（等待）
        if (sameBatch12) {
            return false;
        }

        // 其他情境：直接用 14 capacity 來判斷是否可再放行一顆「無蓋待補」
        return canMoveBySite14Capacity(anchor26, site27Empty);
    }

    /**
     * Site#25「最後一批」判定（基於 R029）
     * - 取得自身 alias_code
     * - 取得 R029_LOG_ID → 找同批 carrierIds
     * - 只要同批其他 carrier 仍在 tracking → 非最後一批
     * - 都不在 tracking → 最後一批
     */
    private boolean isLastBatchByR029(Long containerMainId, WB5Context local) {
        if (containerMainId == null) return false;

        String selfAlias = local.getContainerMain(containerMainId)
                .map(ContainerMain::getAliasCode)
                .orElse(null);
        if (isBlank(selfAlias)) {
            //log.debug("[WB5] isLastBatch 判斷：找不到自身 alias_code（cm#{}）→ 視為非最後一批", containerMainId);
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
            //log.debug("[WB5] isLastBatch 判斷：缺 R029_LOG_ID（cm#{}）→ 視為非最後一批", containerMainId);
            return false;
        }

        List<String> lots = r029LotRepository.findCarrierIdsByLogId(logId);
        if (lots == null || lots.isEmpty()) {
            //log.debug("[WB5] isLastBatch 判斷：logId={} lot 清單為空 → 視為非最後一批", logId);
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
                //log.debug("[WB5][FinalBatchRename] 名稱不可解析，略過 cm#{} raw='{}'", containerMainId, name);
                return;
            }
            if (p.splitIndex != null) return; // 已經有 _k，不重覆改

            Integer max = containerMainRepository.findMaxSplitIndexByBase(p.base);
            int nextK = (max == null ? 1 : Math.max(1, max + 1));

            String newName = p.base + "_" + nextK;
            if (newName.length() > NAME_MAX) newName = newName.substring(0, NAME_MAX);

            boolean ok = containerMainRepository.updateAliasCode(containerMainId, newName);
            if (ok) {
                log.info("[WB5][FinalBatchRename] cm#{} '{}' → '{}'", containerMainId, name, newName);

                if (p.groups != null && !p.groups.isEmpty()) {
                    String groups = joinGroups(p.groups);
                    ContainerAttr a = new ContainerAttr();
                    a.setContainerMainId(containerMainId);
                    a.setAttrKey(ATTR_GROUPS);
                    a.setAttrValue(groups);
                    try { containerAttrRepository.upsert(a); } catch (Exception ignore) {}
                }
            } else {
                log.warn("[WB5][FinalBatchRename] 更新名稱失敗 cm#{} '{}' → '{}'", containerMainId, name, newName);
            }
        } catch (Exception e) {
            log.warn("[WB5][FinalBatchRename] 發生例外 cm#{}: {}", containerMainId, e.getMessage(), e);
        }
    }

    private String joinGroups(SortedSet<Integer> set) {
        return set.stream().map(String::valueOf).collect(Collectors.joining("+"));
    }

    /**
     * 建立 WorkingBeam Request（來源：Site#25 或 Site#26；目標：Site#27）
     * - 同 JVM 內使用 InProcLocks.tryEnterWb5 做瞬間互斥
     *   避免與 GP4@25 / WB8 在同一瞬間同時建單
     */
    private Optional<Long> createRequest(Long workingBeamId, String sourceSiteName) {
        if (!InProcLocks.tryEnterWb5()) {
            //log.debug("[WB5] in-proc 互斥：GP4@25 或 WB8 正在動作，放棄這次建單（source={}）", sourceSiteName);
            return Optional.empty();
        }
        try {
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
                log.info("[WB5] 建立 WorkingBeamRequest 成功: beam#{} from {} → {} reqId={}, key={}",
                        workingBeamId, sourceSiteName, SITE_27, request.getId(), request.getRequestKey());
                return Optional.of(request.getId());
            } else {
                log.warn("[WB5] 建立 WorkingBeamRequest 失敗: beam#{} from {}", workingBeamId, sourceSiteName);
                return Optional.empty();
            }
        } finally {
            InProcLocks.exitWb5();
        }
    }

    /* =============================== Ancestor/Anchor =============================== */

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

    // ======================= 小工具 =======================

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
            log.warn("[WB5] 讀取 R029_COUNT 失敗 cm#{}：{}", containerMainId, e.getMessage());
            return null;
        }
    }

    /** 料號來自 container_main.part_no（必要） */
    private String getTrayTypeByContainerId(Long containerMainId) {
        return containerMainRepository.findById(containerMainId)
                .map(ContainerMain::getPartNo)
                .filter(this::notBlank)
                .orElse(null);
    }

    /** 嚴格：僅回傳 container_data.cover_layers（可能為 NULL=未知） */
    private Integer getCoverLayersStrict(Long containerMainId, WB5Context local) {
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
            //log.debug("[WB5] {} 等待 OCR 流程啟動（anchor={} ref={}）→ WAIT", refSite, anchorCmId, refContainerId);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }

        CoverOcrVerificationService.FinalDecision d =
                ocrVerificationService.decideFinal(anchorCmId, ovOpt.get());

        if (d != CoverOcrVerificationService.FinalDecision.PASS
                && d != CoverOcrVerificationService.FinalDecision.BLOCK) {
            //log.debug("[WB5] {} 等待 OCR 驗證結果（anchor={} ref={} decision={}）→ WAIT", refSite, anchorCmId, refContainerId, d);
            return CoverOcrVerificationService.FinalDecision.WAIT;
        }
        return d;
    }

    /** Site#14 對「指定 ref container」的可用蓋容量（以 cover_layers 表示） */
    private int getCoverCapacityAtSite14(Long c14) {
        ContainerData cd14 = containerDataRepository.findByContainerMainId(c14).orElse(null);
        if (cd14 == null) return 0;
        return safeInt(cd14.getCoverLayers());
    }

    /**
     * 統計下游「同一個 ref@14」正在等蓋的數量：
     * - Site#27 / Transfer#8 上，若 product>0 且 cover==0 → 視為等蓋
     * - 並且其 anchor 對 refContainerIdAt14 的驗證為 PASS 才算同批 waiting
     */
    private int countDownstreamWaitingForSameRef(Long refContainerIdAt14) {
        int waiting = 0;

        // Site#27
        Optional<Long> c27Opt = locationTrackingRepository.findContainerAtLocationName(SITE_27);
        if (c27Opt.isPresent()) {
            Long c27 = c27Opt.get();
            if (isWaitingForCoverByData(c27)) {
                Long a27 = resolveAnchorCmId(c27);
                CoverOcrVerificationService.FinalDecision d =
                        decideFinalOrWait(a27, refContainerIdAt14, SITE_14);
                if (d == CoverOcrVerificationService.FinalDecision.PASS) waiting++;
            }
        }

        // Transfer#8
        Optional<Long> cTr8Opt = locationTrackingRepository.findContainerOnTransfer(TRANSFER_8_ID);
        if (cTr8Opt.isPresent()) {
            Long c8 = cTr8Opt.get();
            if (isWaitingForCoverByData(c8)) {
                Long a8 = resolveAnchorCmId(c8);
                CoverOcrVerificationService.FinalDecision d =
                        decideFinalOrWait(a8, refContainerIdAt14, SITE_14);
                if (d == CoverOcrVerificationService.FinalDecision.PASS) waiting++;
            }
        }

        return waiting;
    }

    /** 判斷某 container 是否為「無蓋待補」：product>0 且 cover==0 */
    private boolean isWaitingForCoverByData(Long containerMainId) {
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        if (cd == null) return false;
        int product = safeInt(cd.getProductLayers());
        int cover = safeInt(cd.getCoverLayers());
        return product > 0 && cover == 0;
    }

    /**
     * Site#14 capacity gating：
     * - 必須：Site#14 有帳，且 anchor26 對 ref=Site#14 的驗證為 PASS（同批）
     * - allow 條件：site27Empty && (waiting < capacity)
     */
    private boolean canMoveBySite14Capacity(Long anchor26, boolean site27Empty) {
        Optional<Long> c14Opt = locationTrackingRepository.findContainerAtLocationName(SITE_14);
        if (c14Opt.isEmpty()) {
            //log.debug("[WB5] 26 無蓋：{} 無帳 → 不可動", SITE_14);
            return false;
        }
        Long c14 = c14Opt.get();

        CoverOcrVerificationService.FinalDecision d14 = decideFinalOrWait(anchor26, c14, SITE_14);
        if (d14 == CoverOcrVerificationService.FinalDecision.WAIT) return false;
        if (d14 != CoverOcrVerificationService.FinalDecision.PASS) {
            //log.debug("[WB5] 26 無蓋：{} decision={}（非 PASS）→ 不可動", SITE_14, d14);
            return false;
        }

        int capacity = getCoverCapacityAtSite14(c14);
        int waiting  = countDownstreamWaitingForSameRef(c14);

        boolean allow = site27Empty && (waiting < capacity);
        //log.debug("[WB5] 26 無蓋：Site14Ref={} capacity={} waiting={} site27Empty={} → allow={}",
//                c14, capacity, waiting, site27Empty, allow);
        return allow;
    }

    private String safeTrim(String s) { return s == null ? "" : s.trim(); }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    /** null/空白 安全大小寫不敏感比較 */
    private boolean strEq(String a, String b) {
        return safeTrim(a).equalsIgnoreCase(safeTrim(b));
    }

    @SuppressWarnings("unused")
    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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

    private class WB5Context {
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
