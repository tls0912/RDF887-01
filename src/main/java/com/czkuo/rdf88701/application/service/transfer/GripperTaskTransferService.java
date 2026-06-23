package com.czkuo.rdf88701.application.service.transfer;

import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.czkuo.rdf88701.common.constants.R029AttrKeys.*;

/**
 * Gripper 任務完成 → 帳籍結果處理（完整註解修正版）
 *
 * <pre>
 * 【名詞】
 *   - moving：夾爪上那一顆（DROP 前）
 *   - left  ：拆批後留在原站那一顆
 *
 * 【層次定義】
 *   三路層數：工蓋(workerCover) / 上蓋(topCover) / 一般(normal)
 *   estimated = 三者總和；verified 由量測端維護，不在此覆蓋。
 *
 * 【站點角色】
 *   1) 拆批站：Site#25、Site#36
 *      - A. 拆批（含一般片 normal>0 被夾走）：
 *           moving=舊 id（夾走到 gripper）
 *           left  =新 id（留在原站），命名 <base>_<k>，k 從 1 增。
 *      - B. 上蓋周轉（cover-only：只搬上/工蓋，normal==0）：
 *           不產生 _k；原站舊 id/名稱維持不變，只更新其分佈。
 *           另建「臨時 moving」（非 <base>_<k> 命名）載蓋去周轉站（#24/#35）。
 *
 *   2) 周轉池（Cover Pool）：Site#24、Site#35
 *      - 屬於「供料/周轉」站。當此站點發生「部分搬」時：
 *        * 不希望站上剩餘帳改名為 _k（要維持原名，例如 63ZACPC581_P_2）。
 *        * moving 可以改成臨時名（不可解析），left 保留原名與群組。
 *        * 這需要「名稱交接」：先將來源帳（將成為 moving）改成臨時名，再複製一顆 left 用回原名。
 *
 *   3) 預建站：Site#12、Site#14（PICK 前先建 moving 新帳）。
 *
 * 【DROP 規則】
 *   - 目標為空：整顆落下。
 *   - 目標有帳：整顆併入（逐項相加）。
 *   - 名稱合併（群組聯集）：
 *        * 一般站：target ← <head>_P_<g1+g2+...>（不帶 _k）。
 *        * 拆批站（#25/#36）：target ← <head>_P_<g1+g2+...>_<nextIdx>（<baseUnion> 的下一個 _k）。
 *          （對應你的期待：在 #36 形成 6+2 時，名稱直接成為 63ZACPC581_P_1+2_1）
 *
 * 【R029 上下文】
 *   - 以 container_attr 暫存：LOG_ID、COUNT、TID、CMD_ID，以及本檔新增的 GROUPS（r029_groups）。
 *   - GROUPS 用於在名稱不可解析時，仍可做群組聯集。
 *
 * 注意：本類僅處理帳與名稱的一致性；實際「周轉站」間搬移由外部流程/任務決定。
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GripperTaskTransferService {

    // ===== 命名與容量常數 =====
    private static final int MAX_PIECES = 22;   // 容量（保守告警用）
    private static final int NAME_MAX = 20;   // 名稱最長 20 字元（含 _P_、+、_k 等）
    private static final String ATTR_GROUPS = "r029_groups";   // 群組快取（如 "1" 或 "1+2"）
    private static final String ATTR_SEQ_PREFIX = "r029_seq";  // 範圍序號前綴

    // ===== 拆批紀錄原身 ID =====
    private static final String ATTR_PARENT = "LINEAGE_PARENT_CMID";
    private static final String ATTR_ROOT = "LINEAGE_ROOT_CMID";

    // ===== 異物檢 attr 錨點 =====
    private static final String ATTR_INSPECT_JOB_ID = "INSPECT_JOB_ID";
    private static final String ATTR_INSPECT_ROLE = "INSPECT_ROLE";
    private static final String ROLE_MOVING = "MOVING";
    private static final String ROLE_LEFT = "LEFT";
    private static final String ATTR_INSPECT_BIND_TS = "INSPECT_BIND_TS";      // 可選：綁定時間
    private static final String ATTR_INSPECT_BIND_GRIP = "INSPECT_BIND_GRIPPER"; // 可選：是哪支夾爪綁的

    // ===== 異物檢任務狀態（與相機/Generator 對齊） =====
    private static final String J_CREATED = "CREATED";
    private static final String J_WAIT_MOVE_FIRST = "WAIT_MOVE_FIRST";
    private static final String J_MOVING_FIRST = "MOVING_FIRST";
    private static final String J_FIRST_DONE = "FIRST_DONE";
    private static final String J_WAIT_MOVE_SECOND = "WAIT_MOVE_SECOND";
    private static final String J_MOVING_SECOND = "MOVING_SECOND";
    private static final String J_SECOND_DONE = "SECOND_DONE";
    private static final String J_DONE = "DONE";
    private static final String J_FAILED = "FAILED";
    private static final Map<String, Long> LocationIdCache = new ConcurrentHashMap<>();

    // ========= VIRTUAL#12/13/14 → Transfer#8 目標正規化（以名稱解析，並做快取） =========
    private static final List<String> VIRTUAL_TO_T8_NAMES = List.of("VIRTUAL#12", "VIRTUAL#13", "VIRTUAL#14");
    private static final String TRANSFER8_NAME = "Transfer#8";

    // 快取：三個 VIRTUAL 的 id 集合、與 Transfer#8 的 id
    private volatile Set<Long> cachedVirtualToT8Ids = null;
    private volatile Long cachedTransfer8Id = null;

    private final LocationFlowRepository locationFlowRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationPointRepository locationPointRepository;
    private final GripperTaskRepository gripperTaskRepository;

    private final ContainerDataRepository containerDataRepository;
    private final ContainerMainRepository containerMainRepository;

    // container_attr 讀寫（過渡做法）
    private final ContainerAttrRepository containerAttrRepository;

    // 查目前夾爪上的進行中 inspection job
    private final InspectionJobRepository inspectionJobRepository;
    private final InspectionRouteMapRepository inspectionRouteMapRepository;
    private final ThreadLocal<TaskContext> taskContextHolder = new ThreadLocal<>();


    // ======================= 入口 =======================
    @Transactional
    public void updateFlowAndTrackingOnSuccess(GripperTask task) {
        taskContextHolder.set(new TaskContext());
        try {
            String type = task.getTaskType(); // MOVE / PICK / DROP
            switch (type) {
                case "MOVE" -> handleMove(task);
                case "PICK" -> handlePickResult(task);
                case "DROP" -> handleDropResult(task);
                default -> log.warn("[Gripper帳籍] 未知任務型別：{} (task#{})", type, task.getId());
            }
        } finally {
            taskContextHolder.remove();
        }
    }

    // ======================= MOVE =======================
    private void handleMove(GripperTask task) {
        //log.debug("[Gripper帳籍] MOVE 類型，無帳籍異動 (task#{})", task.getId());
    }

    // ======================= PICK =======================
    private void handlePickResult(GripperTask task) {
        final Long taskId = task.getId();
        final Long srcIdOrMovingNewId = task.getContainerMainId(); // 來源容器（不再有預建 moving 特例）
        final Long fromLocationId = task.getFromLocationId();
        final int leaveQtyRaw = clampNonNegative(task.getLayerCount()); // 來源需留下的片數

        if (fromLocationId == null) {
            log.warn("[PICK] 缺 fromLocationId，task#{}；將以 tracking 現況處理", taskId);
        }

        Long gripperLocId = resolveGripperLocationId(task.getGripperId());
        if (gripperLocId == null) {
            log.warn("[PICK] 找不到 Gripper 站點：gripper#{} task#{}；仍繼續落帳（flow/track 可能偏差）", task.getGripperId(), taskId);
        }

        // 一般情況：讀來源現況
        var trOpt = locationTrackingRepository.findByContainerMainId(srcIdOrMovingNewId);
        Long currentLocId = trOpt.map(LocationTracking::getLocationPointId).orElse(fromLocationId);

        LayerBreakdown src = getBreakdown(srcIdOrMovingNewId);
        int currentQty = Math.max(0, src.total());

        // 整顆搬到夾爪（來源留下 0）
        if (leaveQtyRaw == 0) {
            markPreviousAsLeftAndVacant(srcIdOrMovingNewId, taskId);
            if (gripperLocId != null) markArrived(srcIdOrMovingNewId, gripperLocId, taskId);
            writeBreakdown(srcIdOrMovingNewId, normalizeToTotal(src, currentQty));
            return;
        }

        // 部分搬 → 計算搬走與留下
        int leftQty = Math.min(leaveQtyRaw, currentQty); // 留在原站
        int moveQty = Math.max(currentQty - leftQty, 0); // 夾走
        SplitResult split = splitFromTop(src, moveQty);  // moving=搬走，left=留下

        // ====== 補給站（Site#12 / Site#14）：不 Vacant，來源留在原地；新建「臨時 moving」到夾爪 ======
        if (isSupplySite(fromLocationId)) {
            // 這兩站是 ALL_COVER，理論上 moving.normal 應為 0（只搬蓋）
            if (!isCoverOnly(split.moving)) {
                log.warn("[PICK@SUPPLY] task#{} 供料站出現一般片 normal>0（不預期），仍以 cover-only 策略處理。", taskId);
            }

            // (1) 更新來源帳的分佈（留在原站，不改名、不 Vacant）
            writeBreakdown(srcIdOrMovingNewId, split.left);

            // (2) 建立「臨時 moving」（不可解析名，<=20）載蓋至夾爪
            String srcName = getName(srcIdOrMovingNewId, "CN-COVER");
            String tmpMovingName = genCoverMovingName(srcName); // 會自動控制長度
            Long movingNewId = containerMainRepository.createFromParent(srcIdOrMovingNewId, tmpMovingName);
            writeBreakdown(movingNewId, split.moving);
            bindLineage(srcIdOrMovingNewId, movingNewId);
            if (gripperLocId != null) markArrived(movingNewId, gripperLocId, taskId);

            // (3) 讓後續 DROP 跟著臨時 moving
            try {
                gripperTaskRepository.updateContainerMainIdIfUnchanged(taskId, srcIdOrMovingNewId, movingNewId);
            } catch (Exception ignore) {
            }

            // (4) 同步必要屬性（群組 / R029 / 厚度）
            ensureGroupsAttrFromName(srcIdOrMovingNewId, srcName);
            ensureGroupsAttrFromName(movingNewId, srcName);
            copyR029Context(srcIdOrMovingNewId, movingNewId);
            copyThickness(srcIdOrMovingNewId, movingNewId);

            log.info("[PICK@SUPPLY] task#{} src#{}(留原站) ←(寫回)；moving#{}={} → gripper（cover-only）",
                    taskId, srcIdOrMovingNewId, movingNewId, tmpMovingName);
            return;
        }

        // ====== 拆批站（Site#25 / #36） ======
        if (isSplitSite(fromLocationId)) {

            // B) 只搬蓋（上蓋/工蓋），不產生 _k，不 Vacant 原站（與補給站策略相同）
            if (isCoverOnly(split.moving)) {
                writeBreakdown(srcIdOrMovingNewId, split.left);
                String srcName = getName(srcIdOrMovingNewId, "CN-COVER");
                String tmpMovingName = genCoverMovingName(srcName);
                Long movingNewId = containerMainRepository.createFromParent(srcIdOrMovingNewId, tmpMovingName);
                writeBreakdown(movingNewId, split.moving);
                if (gripperLocId != null) markArrived(movingNewId, gripperLocId, taskId);
                try {
                    gripperTaskRepository.updateContainerMainIdIfUnchanged(taskId, srcIdOrMovingNewId, movingNewId);
                } catch (Exception ignore) {
                }
                ensureGroupsAttrFromName(srcIdOrMovingNewId, srcName);
                ensureGroupsAttrFromName(movingNewId, srcName);
                copyOcrText(srcIdOrMovingNewId, movingNewId);
                copyR029Context(srcIdOrMovingNewId, movingNewId);
                copyThickness(srcIdOrMovingNewId, movingNewId);

                // ★★★ 若這次只搬蓋導致一般片露出，仍需建/綁異物檢 ★★★
                boolean created = false;
                if (willExposeProductAfterPick(src, split.left.total())) {
                    created = maybeCreateInspectionJobAndBind(
                            task.getGripperId(),
                            movingNewId,           // moving：臨時名那顆（夾走）
                            srcIdOrMovingNewId,    // left  ：留在原站的那顆（同一個 id）
                            fromLocationId
                    );
                }
                if (!created) {
                    // 若已有進行中 job，仍補上綁定，避免漏綁
                    linkToInspectionIfAny(task.getGripperId(), movingNewId, srcIdOrMovingNewId);
                }

                return;
            }

            // A) 正常拆批（含一般片）→ moving=舊 id；left=新 id(<base>_<k>) 留在原站
            markPreviousAsLeftAndVacant(srcIdOrMovingNewId, taskId);
            if (gripperLocId != null) markArrived(srcIdOrMovingNewId, gripperLocId, taskId);
            writeBreakdown(srcIdOrMovingNewId, split.moving); // 舊 id = 被夾走的那把

            String srcName = getName(srcIdOrMovingNewId, "UNKNOWN");
            NameParts np = NameParts.parse(srcName);

            String tag = (np.tag == null || np.tag.isBlank()) ? "T" : np.tag;
            String groups = np.isParsable() ? joinGroups(np.groups) : readGroupsStringOrDefault(srcIdOrMovingNewId, "1");
            String head = np.isParsable() ? np.head : safeHeadForKey(srcName);

            int nextIdx = nextKByR029Scope(srcIdOrMovingNewId, head, tag, groups);
            String newLeftName = np.isParsable()
                    ? composeNameWithinLimit(np.head, tag, groups, nextIdx)
                    : limitRawNameWithIndex(srcName, nextIdx);

            Long newLeftId = containerMainRepository.createFromParent(srcIdOrMovingNewId, newLeftName);
            writeBreakdown(newLeftId, split.left);
            bindLineageIfProduct(srcIdOrMovingNewId, newLeftId, split.left);
            if (currentLocId != null) markArrived(newLeftId, currentLocId, taskId);

            // 序號鏡射到新 left，下一次從 left 拆仍能接續
            mirrorSeqTo(srcIdOrMovingNewId, newLeftId, head, tag, groups);

            copyOcrText(srcIdOrMovingNewId, newLeftId);
            copyR029Context(srcIdOrMovingNewId, newLeftId);
            copyThickness(srcIdOrMovingNewId, newLeftId);
            ensureGroupsAttrFromName(srcIdOrMovingNewId, srcName);
            ensureGroupsAttrFromName(newLeftId, newLeftName);

            // → 依「是否露出一般片」決定是否建立異物檢任務（moving=srcIdOrMovingNewId, left=newLeftId）
            boolean created = false;
            if (willExposeProductAfterPick(src, split.left.total())) {
                created = maybeCreateInspectionJobAndBind(task.getGripperId(), srcIdOrMovingNewId, newLeftId, fromLocationId);
            }
            if (!created) {
                // 若已存在進行中檢測單，仍綁當前 moving/left（避免漏綁）
                linkToInspectionIfAny(task.getGripperId(), srcIdOrMovingNewId, newLeftId);
            }
            return;
        }

        // ====== 周轉池（Site#24 / #35）：名稱交接，left 保留原名 ======
        if (isCoverPoolSite(fromLocationId)) {
            String originalName = getName(srcIdOrMovingNewId, randomName());
            ensureGroupsAttrFromName(srcIdOrMovingNewId, originalName);

            // moving 先改臨時名，釋放原名給 left 使用
            String tempName = genCoverMovingName(originalName);
            try {
                containerMainRepository.updateAliasCode(srcIdOrMovingNewId, tempName);
            } catch (Exception ignore) {
            }
            markPreviousAsLeftAndVacant(srcIdOrMovingNewId, taskId);

            Long leftId = containerMainRepository.createFromParent(
                    srcIdOrMovingNewId, truncate(originalName, NAME_MAX));

            // 先複製 container_data 的「非層數/數量」欄位
            cloneContainerDataMeta(srcIdOrMovingNewId, leftId);

            // 再寫 left 的層數
            writeBreakdown(leftId, split.left);
            bindLineageIfProduct(srcIdOrMovingNewId, leftId, split.left);
            if (currentLocId != null) markArrived(leftId, currentLocId, taskId);

            // moving（舊 id）寫搬走的層數
            writeBreakdown(srcIdOrMovingNewId, split.moving);
            if (gripperLocId != null) markArrived(srcIdOrMovingNewId, gripperLocId, taskId);

            propagateGroupsAttr(srcIdOrMovingNewId, leftId);
            copyR029Context(srcIdOrMovingNewId, leftId);
            copyThickness(srcIdOrMovingNewId, leftId);

            // → 依「是否露出一般片」決定是否建立異物檢任務
            boolean created = false;
            if (willExposeProductAfterPick(src, split.left.total())) {
                created = maybeCreateInspectionJobAndBind(task.getGripperId(), srcIdOrMovingNewId, leftId, fromLocationId);
            }
            if (!created) {
                linkToInspectionIfAny(task.getGripperId(), srcIdOrMovingNewId, leftId);
            }
            return;
        }

        // ====== 其他站（預設行為） ======
        markPreviousAsLeftAndVacant(srcIdOrMovingNewId, taskId);
        if (gripperLocId != null) markArrived(srcIdOrMovingNewId, gripperLocId, taskId);
        writeBreakdown(srcIdOrMovingNewId, split.moving);

        String srcName = getName(srcIdOrMovingNewId, "UNKNOWN");
        NameParts np = NameParts.parse(srcName);
        String tag = (np.tag == null || np.tag.isBlank()) ? "T" : np.tag;
        String groups = np.isParsable() ? joinGroups(np.groups) : readGroupsStringOrDefault(srcIdOrMovingNewId, "1");
        String head = np.isParsable() ? np.head : safeHeadForKey(srcName);

        int nextIdx = nextKByR029Scope(srcIdOrMovingNewId, head, tag, groups);
        String newName = np.isParsable()
                ? composeNameWithinLimit(np.head, tag, groups, nextIdx)
                : limitRawNameWithIndex(srcName, nextIdx);

        Long newContainerId = containerMainRepository.createFromParent(srcIdOrMovingNewId, newName);
        writeBreakdown(newContainerId, split.left);
        bindLineageIfProduct(srcIdOrMovingNewId, newContainerId, split.left);
        if (currentLocId != null) markArrived(newContainerId, currentLocId, taskId);

        mirrorSeqTo(srcIdOrMovingNewId, newContainerId, head, tag, groups);
        copyR029Context(srcIdOrMovingNewId, newContainerId);
        copyThickness(srcIdOrMovingNewId, newContainerId);
        ensureGroupsAttrFromName(srcIdOrMovingNewId, srcName);
        ensureGroupsAttrFromName(newContainerId, newName);

        // → 依「是否露出一般片」決定是否建立異物檢任務
        boolean created = false;
        if (willExposeProductAfterPick(src, split.left.total())) {
            created = maybeCreateInspectionJobAndBind(task.getGripperId(), srcIdOrMovingNewId, newContainerId, fromLocationId);
        }
        if (!created) {
            linkToInspectionIfAny(task.getGripperId(), srcIdOrMovingNewId, newContainerId);
        }
    }

    // ======================= DROP =======================
    private void handleDropResult(GripperTask task) {
        final Long taskId = task.getId();
        final Long movingId = task.getContainerMainId();   // 夾爪上的那顆帳
        Long toLocationId = task.getToLocationId();

        LayerBreakdown moving = getBreakdown(movingId);

        if (toLocationId == 270L || toLocationId == 271L || toLocationId == 272L) {
            toLocationId = 257L;
        }

        Optional<Long> targetOpt = (toLocationId == null) ? Optional.empty() : findContainerByLocationId(toLocationId);

        // 目標為空：整顆落下
        if (targetOpt.isEmpty()) {
            markPreviousAsLeftOnGripperOnly(movingId, taskId);
            if (toLocationId != null) markArrived(movingId, toLocationId, taskId);
            writeBreakdown(movingId, normalizeToTotal(moving, moving.total()));
            return;
        }

        // 目標有容器：整顆併入（逐項相加）
        Long targetId = targetOpt.get();
        LayerBreakdown target = getBreakdown(targetId);

        LayerBreakdown merged = merge(target, moving);
        writeBreakdown(targetId, merged);
        markPreviousAsLeftOnGripperOnly(movingId, taskId);
        locationTrackingRepository.deleteByContainerMainId(movingId);

        // 如果這次 DROP 是「只搬蓋（上蓋/工蓋）」且沒有一般片(normal)，
        // 就只是單純併入，不改變目標名稱，也不更新群組屬性（因為群組代表產品群組，蓋子周轉不該影響）。
        if (isCoverOnly(moving)) {
            String keepName = getName(targetId, null);
            log.info("[DROP@COVER-ONLY] task#{} moving(cover-only) → target#{}，僅併入不改名（維持 {}）",
                    taskId, targetId, keepName);
            return; // 直接結束，不進入名稱合併邏輯
        }

        // === 名稱合併（群組聯集，僅在此次 DROP 含一般片時才會進來） ===
        try {
            String targetName = getName(targetId, null);
            String movingName = getName(movingId, null);

            NameParts t = (targetName == null) ? new NameParts(null, null, null, null, null) : NameParts.parse(targetName);
            NameParts m = (movingName == null) ? new NameParts(null, null, null, null, null) : NameParts.parse(movingName);

            SortedSet<Integer> gTarget = readGroups(targetId, t);
            SortedSet<Integer> gMoving = readGroups(movingId, m);
            if (!gTarget.isEmpty() || !gMoving.isEmpty()) {
                SortedSet<Integer> union = new TreeSet<>(gTarget);
                union.addAll(gMoving);

                // head/tag 取用邏輯：優先 target 的可解析值，其次 moving 的可解析值；tag 預設 "T"
                String head = (t.isParsable() ? t.head : (m.isParsable() ? m.head : null));
                String tag = (t.tag != null ? t.tag : (m.tag != null ? m.tag : "T"));
                String groups = joinGroups(union);

                boolean targetIsSplitSite = isSplitSite(toLocationId);
                if (head != null) {
                    if (targetIsSplitSite) {
                        // 在拆批站，名稱要帶 _k；序號以「R029 範圍」計數，避免新舊混用
                        int nextIdx = nextKByR029Scope(targetId, head, tag, groups);
                        String newTargetName = composeNameWithinLimit(head, tag, groups, nextIdx);
                        if (targetName == null || !newTargetName.equals(targetName)) {
                            containerMainRepository.updateAliasCode(targetId, newTargetName);
                        }
                        // 此處不需 mirror 到 moving（moving tracking 已刪）
                    } else {
                        // 非拆批站：僅合併 base，不帶 _k
                        String newTargetName = composeNameWithinLimit(head, tag, groups, null);
                        if (targetName == null || !newTargetName.equals(targetName)) {
                            containerMainRepository.updateAliasCode(targetId, newTargetName);
                        }
                    }
                    // 更新群組屬性（此次含一般片，群組聯集才合理）
                    upsertAttr(targetId, ATTR_GROUPS, groups);
                }
            }
        } catch (Exception e) {
            log.warn("[NAME-MERGE] 目標改名失敗 target#{}: {}", targetId, e.getMessage());
        }
    }

    // ======================= Flow / Tracking 基本操作 =======================
    private void markPreviousAsLeftAndVacant(Long containerId, Long taskId) {
        locationFlowRepository.markPreviousAsLeft(containerId, LocalDateTime.now());
        locationTrackingRepository.findByContainerMainId(containerId).ifPresent(tr -> {
            Long prevLoc = tr.getLocationPointId();
            locationTrackingRepository.deleteByContainerMainId(containerId);
            if (prevLoc != null) locationPointRepository.markVacant(prevLoc);
        });
    }

    private void markPreviousAsLeftOnGripperOnly(Long containerId, Long taskId) {
        locationFlowRepository.markPreviousAsLeft(containerId, LocalDateTime.now());
        locationTrackingRepository.deleteByContainerMainId(containerId);
    }

    private void markArrived(Long containerId, Long locationId, Long taskId) {
        LocationFlow flow = new LocationFlow();
        flow.setContainerMainId(containerId);
        flow.setLocationPointId(locationId);
        flow.setArrivedTime(LocalDateTime.now());
        flow.setEntryType("PLC");
        flow.setSourceTaskId(taskId);
        locationFlowRepository.insert(flow);

        locationTrackingRepository.findByContainerMainId(containerId).ifPresentOrElse(t -> {
            locationTrackingRepository.updateLocation(containerId, locationId, flow.getId());
        }, () -> {
            LocationTracking t = new LocationTracking();
            t.setContainerMainId(containerId);
            t.setLocationPointId(locationId);
            t.setArrivedTime(LocalDateTime.now());
            t.setFlowId(flow.getId());
            locationTrackingRepository.save(t);
        });

        if (locationId != null) locationPointRepository.markOccupied(locationId);
    }

    private Long resolveGripperLocationId(Long gripperId) {
        if (gripperId == null) return null;
        String expected = "Gripper#" + gripperId;
        return getLocationId(expected).orElse(null);
    }

    private Optional<Long> findContainerByLocationId(Long locationPointId) {
        if (locationPointId == null) return Optional.empty();
        return locationTrackingRepository.findByLocationPointId(locationPointId).map(LocationTracking::getContainerMainId);
    }

    // ======================= Container 數據：三類層數處理 =======================

    /**
     * 層數結構：工蓋 / 上蓋 / 一般；estimated = 三者總和
     */
    private static final class LayerBreakdown {
        int workerCover; // 工蓋（特殊蓋）
        int topCover;    // 上蓋
        int normal;      // 一般

        int total() {
            return Math.max(0, workerCover) + Math.max(0, topCover) + Math.max(0, normal);
        }
    }

    /**
     * 是否為「只搬蓋（上蓋+工蓋）且無一般片」
     */
    private boolean isCoverOnly(LayerBreakdown b) {
        if (b == null) return false;
        int n = Math.max(0, b.normal);
        int t = Math.max(0, b.topCover);
        int w = Math.max(0, b.workerCover);
        return n == 0 && (t + w) > 0;
    }

    /**
     * 取 container 的三類層數；以 estimated 為主，必要時做保守回推與一致性紀錄。
     */
    private LayerBreakdown getBreakdown(Long containerMainId) {
        LayerBreakdown b = new LayerBreakdown();
        if (containerMainId == null) return b;

        ContainerData cd = readOrCreate(containerMainId);

        Integer wc = cd.getWorkCoverLayers();
        Integer tc = cd.getCoverLayers();
        Integer prod = cd.getProductLayers();
        Integer est = cd.getEstimatedQuantity();
        Integer ver = cd.getVerifiedQuantity();
        String kind = cd.getContentKind();

        int sum = nvl(wc) + nvl(tc) + nvl(prod);

        if (sum == 0 && nvl(est) > 0) {
            if ("ALL_COVER".equals(kind)) {
                b.topCover = nvl(est);
            } else if ("NORMAL_NO_COVER".equals(kind) || "EMPTY".equals(kind)) {
                b.normal = nvl(est);
            } else {
                b.topCover = 1;
                b.normal = Math.max(0, nvl(est) - 1);
            }
            logConsistency("getBreakdown-infer", containerMainId, est, ver, b.total(), kind, "kind=" + kind);
            return b;
        }

        b.workerCover = nvl(wc);
        b.topCover = nvl(tc);
        b.normal = nvl(prod);

        if (nvl(est) != b.total()) {
            logConsistency("getBreakdown-preNormalize", containerMainId, est, ver, b.total(), kind, "before normalize");
            b = normalizeToTotal(b, nvl(est));
        } else {
            logConsistency("getBreakdown", containerMainId, est, ver, b.total(), kind, null);
        }
        return b;
    }

    /**
     * 將層數校正為指定 total。若不足→補到一般；若超出→依 普→上→工 的順序回削。
     */
    private LayerBreakdown normalizeToTotal(LayerBreakdown in, int total) {
        LayerBreakdown b = copy(in);
        int sum = b.total();
        int diff = total - sum;
        if (diff == 0) return b;
        if (diff > 0) {
            b.normal += diff;
        } else {
            int over = -diff;
            int d = Math.min(b.normal, over);
            b.normal -= d;
            over -= d;
            if (over > 0) {
                d = Math.min(b.topCover, over);
                b.topCover -= d;
                over -= d;
            }
            if (over > 0) {
                d = Math.min(b.workerCover, over);
                b.workerCover -= d;
            }
        }
        return b;
    }

    /**
     * 從頂部取走 moveQty 層（上蓋→一般→工蓋），回傳 moving/left。
     */
    private SplitResult splitFromTop(LayerBreakdown src, int moveQty) {
        LayerBreakdown left = copy(src);
        LayerBreakdown moving = new LayerBreakdown();
        int take = Math.max(0, moveQty);
        int t = Math.min(left.topCover, take);
        moving.topCover += t;
        left.topCover -= t;
        take -= t;
        t = Math.min(left.normal, take);
        moving.normal += t;
        left.normal -= t;
        take -= t;
        t = Math.min(left.workerCover, take);
        moving.workerCover += t;
        left.workerCover -= t;
        take -= t;
        moving = normalizeToTotal(moving, moveQty);
        left = normalizeToTotal(left, Math.max(0, src.total() - moveQty));
        return new SplitResult(moving, left);
    }

    /**
     * 併入：逐項相加
     */
    private LayerBreakdown merge(LayerBreakdown a, LayerBreakdown b) {
        LayerBreakdown r = new LayerBreakdown();
        r.workerCover = a.workerCover + b.workerCover;
        r.topCover = a.topCover + b.topCover;
        r.normal = a.normal + b.normal;
        return r;
    }

    private static final class SplitResult {
        final LayerBreakdown moving, left;

        SplitResult(LayerBreakdown m, LayerBreakdown l) {
            this.moving = m;
            this.left = l;
        }
    }

    private LayerBreakdown copy(LayerBreakdown in) {
        LayerBreakdown b = new LayerBreakdown();
        b.workerCover = in.workerCover;
        b.topCover = in.topCover;
        b.normal = in.normal;
        return b;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private String nvl(String v) {
        return v == null ? "" : v;
    }

    // ======================= 一致性記錄 =======================
    private void logConsistency(String stage, Long containerMainId, Integer estimated, Integer verified, Integer sumLayers, String contentKind, String extra) {
        int est = estimated == null ? 0 : estimated;
        int ver = verified == null ? 0 : verified;
        int sum = sumLayers == null ? 0 : sumLayers;
        if (est != sum) {
            log.warn("[LAYER-MISMATCH][{}] container#{} estimated={} vs sum(layers)={} {}", stage, containerMainId, est, sum, (extra == null ? "" : "| " + extra));
        } else {
            //log.debug("[LAYER-OK][{}] container#{} estimated 與 分項總和一致 = {}", stage, containerMainId, est);
        }
        if (ver > 0 && (ver != est || ver != sum)) {
            log.warn("[VERIFY-MISMATCH][{}] container#{} verified={} vs estimated={} / sum={}", stage, containerMainId, ver, est, sum);
        }
    }

    // ======================= ContainerData 讀寫封裝 =======================
    private ContainerData readOrCreate(Long containerMainId) {
        return taskContext().readOrCreateContainerData(containerMainId);
    }

    private void writeBreakdown(Long containerMainId, LayerBreakdown b) {
        ContainerData before = readOrCreate(containerMainId);
        int beforeSum = nvl(before.getWorkCoverLayers()) + nvl(before.getCoverLayers()) + nvl(before.getProductLayers());
        logConsistency("writeBreakdown-before", containerMainId, before.getEstimatedQuantity(), before.getVerifiedQuantity(), beforeSum, before.getContentKind(), null);

        String beforeKind = nvl(before.getContentKind());
        String contentKind = "UNKNOWN";
        if (b.topCover > 0 && b.normal == 0) contentKind = "ALL_COVER";
        else if (b.topCover > 0 && b.normal > 0) contentKind = "NORMAL_WITH_COVER";
        else if (b.topCover == 0 && b.normal > 0) contentKind = "NORMAL_NO_COVER";
        else if (!beforeKind.isEmpty()) contentKind = beforeKind;

        int newTotal = b.total();
        before.setEstimatedQuantity(newTotal);
        before.setWorkCoverLayers(b.workerCover);
        before.setCoverLayers(b.topCover);
        before.setProductLayers(b.normal);
        before.setContentKind(contentKind);
        containerDataRepository.update(before);

        taskContext().putContainerData(before);
        ContainerData after = before;
        int afterSum = nvl(after.getWorkCoverLayers()) + nvl(after.getCoverLayers()) + nvl(after.getProductLayers());
        logConsistency("writeBreakdown-after", containerMainId, after.getEstimatedQuantity(), after.getVerifiedQuantity(), afterSum, after.getContentKind(), null);
    }

    /**
     * 複製 container_data 的「非層數/數量」欄位（例如 OCR、verified），
     * 層數與 estimated/contentKind 仍由 writeBreakdown(...) 決定。
     */
    private void cloneContainerDataMeta(Long srcContainerId, Long dstContainerId) {
        if (srcContainerId == null || dstContainerId == null) return;

        taskContext().findContainerData(srcContainerId).ifPresent(src -> {
            // 讀或建目標 container_data
            ContainerData dst = readOrCreate(dstContainerId);

            try {
                // 只複製與層數無關的欄位；層數/estimated/contentKind 由 writeBreakdown 負責
                dst.setVerifiedQuantity(src.getVerifiedQuantity());
                dst.setOcrText1(src.getOcrText1());
                dst.setOcrText2(src.getOcrText2());
                // 若還有其他「非層數」欄位（例如 tray 型態/厚度等存放在 data），可在此擴充
                containerDataRepository.update(dst);
                taskContext().putContainerData(dst);
            } catch (Exception e) {
                log.warn("[ContainerData] clone meta 失敗 {}→{} : {}", srcContainerId, dstContainerId, e.getMessage());
            }
        });
    }

    /**
     * 清空（設為 null） verifiedQuantity；若資料不存在會先建一筆預設的 container_data。
     */
    private void clearVerifiedQuantity(Long containerMainId) {
        if (containerMainId == null) return;
        ContainerData cd = readOrCreate(containerMainId);
        if (cd.getVerifiedQuantity() != null) {
            cd.setVerifiedQuantity(null);
            containerDataRepository.update(cd);
            taskContext().putContainerData(cd);
            //log.debug("[VERIFY] 清空 verifiedQuantity：cm#{}", containerMainId);
        }
    }

    /**
     * 方便一次清空多顆
     */
    private void clearVerifiedQuantity(Long... containerIds) {
        if (containerIds == null) return;
        for (Long id : containerIds) clearVerifiedQuantity(id);
    }

    // ======================= 名稱/群組/R029 範圍工具 =======================

    private String getName(Long containerId, String fallback) {
        return containerMainRepository.findById(containerId).map(ContainerMain::getAliasCode).orElse(fallback);
    }

    private void copyOcrText(Long srcContainerId, Long newContainerId) {
        if (srcContainerId == null || newContainerId == null) return;

        taskContext().findContainerData(srcContainerId).ifPresent(src -> {
            // 讀或建目標 container_data
            ContainerData dst = readOrCreate(newContainerId);

            try {
                dst.setOcrText1(src.getOcrText1());
                dst.setOcrText2(src.getOcrText2());
                // 若還有其他「非層數」欄位（例如 tray 型態/厚度等存放在 data），可在此擴充
                containerDataRepository.update(dst);
                taskContext().putContainerData(dst);
            } catch (Exception e) {
                log.warn("[ContainerData] copy ocr text 失敗 {}→{} : {}", srcContainerId, newContainerId, e.getMessage());
            }
        });
    }

    private void bindLineage(Long srcId, Long newId) {
        if (srcId == null || newId == null) return;

        upsertAttr(newId, ATTR_PARENT, String.valueOf(srcId));

        // root：如果 src 已有 root，就沿用；否則 src 自己就是 root
        String root = findAttr(srcId, ATTR_ROOT)
                .map(ContainerAttr::getAttrValue)
                .orElse(String.valueOf(srcId));
        upsertAttr(newId, ATTR_ROOT, root);
    }

    private boolean hasNormal(LayerBreakdown b) {
        return b != null && Math.max(0, b.normal) > 0;
    }

    private void bindLineageIfProduct(Long srcId, Long newId, LayerBreakdown newBreakdown) {
        if (!hasNormal(newBreakdown)) return;
        bindLineage(srcId, newId);
    }

    private void copyR029Context(Long srcContainerId, Long newContainerId) {
        if (srcContainerId == null || newContainerId == null) return;
        List<ContainerAttr> attrs = new ArrayList<>(4);
        findAttr(srcContainerId, LOG_ID).ifPresent(a -> attrs.add(buildAttr(newContainerId, LOG_ID, a.getAttrValue())));
        findAttr(srcContainerId, COUNT).ifPresent(a -> attrs.add(buildAttr(newContainerId, COUNT, a.getAttrValue())));
        findAttr(srcContainerId, TID).ifPresent(a -> attrs.add(buildAttr(newContainerId, TID, a.getAttrValue())));
        findAttr(srcContainerId, CMD_ID).ifPresent(a -> attrs.add(buildAttr(newContainerId, CMD_ID, a.getAttrValue())));
        upsertAttrs(attrs);
    }

    private void copyThickness(Long srcContainerId, Long newContainerId) {
        if (srcContainerId == null || newContainerId == null) return;
        findAttr(srcContainerId, "tray_thickness_mm").ifPresent(a -> upsertAttr(newContainerId, "tray_thickness_mm", a.getAttrValue()));
    }

    private void upsertAttr(Long containerId, String key, String value) {
        ContainerAttr attr = buildAttr(containerId, key, value);
        if (attr == null) return;
        upsertAttrs(List.of(attr));
    }

    private ContainerAttr buildAttr(Long containerId, String key, String value) {
        if (containerId == null || key == null) return null;
        ContainerAttr e = new ContainerAttr();
        e.setContainerMainId(containerId);
        e.setAttrKey(key);
        e.setAttrValue(value);
        return e;
    }

    private void upsertAttrs(List<ContainerAttr> attrs) {
        if (attrs == null || attrs.isEmpty()) return;
        List<ContainerAttr> valid = attrs.stream()
                .filter(Objects::nonNull)
                .filter(e -> e.getContainerMainId() != null && e.getAttrKey() != null)
                .toList();
        if (valid.isEmpty()) return;
        try {
            if (valid.size() == 1) {
                containerAttrRepository.upsert(valid.get(0));
            } else {
                containerAttrRepository.batchUpsert(valid);
            }
            TaskContext ctx = taskContext();
            for (ContainerAttr attr : valid) {
                ctx.attrCache.put(
                        new AttrKey(attr.getContainerMainId(), attr.getAttrKey()),
                        Optional.of(attr)
                );
            }
        } catch (Exception e) {
            log.error("[ATTR] upsert 失敗 size={} : {}", valid.size(), e.getMessage(), e);
        }
    }

    private void ensureGroupsAttrFromName(Long containerId, String name) {
        if (name == null) return;
        NameParts p = NameParts.parse(name);
        if (p.isParsable()) {
            String groups = p.groups.stream().map(String::valueOf).collect(Collectors.joining("+"));
            if (findAttr(containerId, ATTR_GROUPS).isEmpty()) {
                upsertAttr(containerId, ATTR_GROUPS, groups);
            }
        }
    }

    private void propagateGroupsAttr(Long srcId, Long dstId) {
        findAttr(srcId, ATTR_GROUPS).ifPresent(a -> upsertAttr(dstId, ATTR_GROUPS, a.getAttrValue()));
    }

    private SortedSet<Integer> readGroups(Long containerId, NameParts p) {
        if (p != null && p.isParsable()) return new TreeSet<>(p.groups);
        return findAttr(containerId, ATTR_GROUPS)
                .map(a -> parseGroups(a.getAttrValue()))
                .orElseGet(TreeSet::new);
    }

    private String readGroupsStringOrDefault(Long containerId, String defVal) {
        return findAttr(containerId, ATTR_GROUPS).map(ContainerAttr::getAttrValue).orElse(defVal);
    }

    private SortedSet<Integer> parseGroups(String s) {
        SortedSet<Integer> set = new TreeSet<>();
        if (s == null || s.isBlank()) return set;
        for (String part : s.split("\\+")) {
            try {
                set.add(Integer.parseInt(part.trim()));
            } catch (Exception ignore) {
            }
        }
        return set;
    }

    private String joinGroups(SortedSet<Integer> set) {
        return set.stream().map(String::valueOf).collect(Collectors.joining("+"));
    }

    // ===== R029 序號（_k）範圍：以 LOG_ID + tag + groups + headAbbr 建 key，集中在 attr 維護 =====

    /**
     * 取得該 R029 範圍的下一個 k，並把新值寫回到「此容器」的 attr。
     */
    private int nextKByR029Scope(Long containerId, String head, String tag, String groups) {
        String key = buildSeqKey(containerId, head, tag, groups);
        int cur = findAttr(containerId, key)
                .map(a -> safeParseInt(a.getAttrValue()))
                .orElse(0);
        int next = Math.max(0, cur) + 1;
        upsertAttr(containerId, key, String.valueOf(next));
        return next;
    }

    /**
     * 把來源容器上的同一範圍序號鏡射到目標容器（確保之後從任一顆拆都能接續）。
     */
    private void mirrorSeqTo(Long srcContainerId, Long dstContainerId, String head, String tag, String groups) {
        String key = buildSeqKey(srcContainerId, head, tag, groups);
        findAttr(srcContainerId, key).ifPresent(a -> upsertAttr(dstContainerId, key, a.getAttrValue()));
    }

    /**
     * 建立範圍序號 attr key：r029_seq|l=<LOG_ID>|t=<TAG>|g=<groups>|h=<headAbbr>
     */
    private String buildSeqKey(Long containerId, String head, String tag, String groups) {
        String logId = findAttr(containerId, LOG_ID).map(ContainerAttr::getAttrValue).orElse("noLog");
        String t = (tag == null || tag.isBlank()) ? "P" : tag;
        String g = (groups == null || groups.isBlank()) ? "1" : groups;
        String h = safeHeadForKey(head);
        // 注意：container_attr.attr_key 若有長度限制（常見 64），下列長度遠小於 64
        return ATTR_SEQ_PREFIX + "|l=" + logId + "|t=" + t + "|g=" + g + "|h=" + h;
    }

    /**
     * head 在 key 中取最多 12 字，避免 attr_key 過長。
     */
    private String safeHeadForKey(String head) {
        if (head == null || head.isBlank()) return "X";
        return head.length() <= 12 ? head : head.substring(0, 12);
    }

    private int safeParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }

    // ======================= 命名長度限制：工具方法 =======================

    /**
     * 在 20 字內組合：<head>_<tag>_<groups>[_idx]（不塞預設 tag）
     */
    private String composeNameWithinLimit(String head, String tag, String groups, Integer splitIdx) {
        if (head == null) head = "X";
        if (tag == null || tag.isBlank()) throw new IllegalArgumentException("tag is required in strict mode");
        if (groups == null || groups.isBlank()) groups = "1";

        String suffix = "_" + tag + "_" + groups + (splitIdx != null ? "_" + splitIdx : "");
        int headMax = Math.max(1, NAME_MAX - suffix.length());
        String shortHead = head.length() <= headMax ? head : head.substring(0, headMax);
        return shortHead + suffix;
    }


    /**
     * 原字串 + _k，超過就截斷到 20。
     */
    private String limitRawNameWithIndex(String raw, int splitIdx) {
        if (raw == null) raw = "X";
        String s = raw + "_" + splitIdx;
        return truncate(s, NAME_MAX);
    }

    /**
     * 截斷字串到指定長度（安全）。
     */
    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 產生「上蓋周轉」的臨時 moving 名稱：以來源為前綴、加上短碼，整體 <= 20。
     */
    private String genCoverMovingName(String srcName) {
        String suffix = "_CV" + randBase36(3); // 短碼 3 碼即可
        String base = (srcName == null ? "C" : srcName);
        int headMax = Math.max(1, NAME_MAX - suffix.length());
        String prefix = base.length() <= headMax ? base : base.substring(0, headMax);
        return prefix + suffix;
    }

    private String randBase36(int len) {
        String digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int n = RAND.nextInt(digits.length());
            sb.append(digits.charAt(n));
        }
        return sb.toString();
    }

    // ======================= 其他工具 =======================

    private int clampNonNegative(Integer q) {
        return (q == null || q < 0) ? 0 : q;
    }

    private static final String RAND_ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RAND = new SecureRandom();

    /**
     * 產生短隨機名稱（fallback 用；一般不會用於拆分命名）
     */
    private String randomName() {
        StringBuilder sb = new StringBuilder("CN-");
        for (int i = 0; i < 8; i++) sb.append(RAND_ALPHANUM.charAt(RAND.nextInt(RAND_ALPHANUM.length())));
        String s = sb.toString();
        return truncate(s, NAME_MAX);
    }

    // ======================= 任務狀態收尾 =======================
    @Transactional
    public void markTaskCompleted(GripperTask task) {
        if (!"COMPLETED".equals(task.getTaskStatus())) {
            task.setTaskStatus("COMPLETED");
            task.setCompletedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            gripperTaskRepository.update(task);
            log.info("[Gripper任務完成] 任務#{} → COMPLETED", task.getId());
        }
    }

    @Transactional
    public void markTaskFailed(GripperTask task, String reason) {
        if (!"FAILED".equals(task.getTaskStatus())) {
            task.setTaskStatus("FAILED");
            task.setCompletedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            gripperTaskRepository.update(task);
            log.error("[Gripper任務失敗] 任務#{} → FAILED，原因：{}", task.getId(), reason);
        }
    }

    // ======================= 命名解析工具 =======================
    /**
     * 容器名稱解析：
     * 格式一：<head>_<tag>_<groups>[_idx]
     * 例：11TY00V002_P_1、11TY00V002_PASS_1_3、808VCCV001_F_1+2
     * <p>
     * head/tag: [A-Za-z0-9]+
     * groups:   1..9 開頭整數，可用 '+' 連接（不允許 0 起頭）
     * idx:      1..9 開頭整數（不允許 0 起頭）
     */
    private static final Pattern ID_PATTERN_STRICT_WITH_IDX =
            Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*(?:\\+[1-9][0-9]*)*)_([1-9][0-9]*)$");

    // <head>_<tag>_<groups>（沒有 idx）
    private static final Pattern ID_PATTERN_STRICT_BASE =
            Pattern.compile("^([A-Za-z0-9]+)_([A-Za-z0-9]+)_([1-9][0-9]*)$");


    private static final class NameParts {
        final String raw;
        final String head;               // [A-Za-z0-9]+
        final String tag;                // [A-Za-z0-9]+（必有）
        final SortedSet<Integer> groups; // 1, 1+2, ...
        final Integer splitIndex;        // 可能為 null（若原名沒有 _idx）
        final String base;               // <head>_<tag>_<groups>

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

            if (s.length() >= 35) return new NameParts(null, null, null, null, null);

            Matcher m1 = ID_PATTERN_STRICT_WITH_IDX.matcher(s);
            if (m1.matches()) {
                String head = m1.group(1);
                String tag = m1.group(2);
                String gs = m1.group(3);
                Integer idx = Integer.parseInt(m1.group(4));
                SortedSet<Integer> groups = Arrays.stream(gs.split("\\+"))
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(TreeSet::new));
                return new NameParts(s, head, tag, groups, idx);
            }

            Matcher m0 = ID_PATTERN_STRICT_BASE.matcher(s);
            if (m0.matches()) {
                String head = m0.group(1);
                String tag = m0.group(2);
                String gs = m0.group(3);

                SortedSet<Integer> groups = Arrays.stream(gs.split("\\+"))
                        .map(Integer::parseInt)
                        .collect(Collectors.toCollection(TreeSet::new));
                return new NameParts(s, head, tag, groups, null);
            }

            // 不符合嚴格格式 → 視為不可解析
            return new NameParts(s, null, null, null, null);
        }

        boolean isParsable() {
            return head != null && tag != null && groups != null && !groups.isEmpty();
        }
    }

    // ======================= 站點判斷 =======================

    /**
     * 補給站：Site#12 / Site#14（ALL_COVER，PICK 後才新建 moving 帳）
     */
    private boolean isSupplySite(Long locId) {
        if (locId == null) return false;
        Long id12 = getLocationId("Site#12").orElse(null);
        Long id14 = getLocationId("Site#14").orElse(null);
        return Objects.equals(locId, id12) || Objects.equals(locId, id14);
    }

    /**
     * 是否為「拆批站」：Site#25 / Site#36
     */
    private boolean isSplitSite(Long locId) {
        if (locId == null) return false;
        Long id25 = getLocationId("Site#25").orElse(null);
        Long id36 = getLocationId("Site#36").orElse(null);
        return Objects.equals(locId, id25) || Objects.equals(locId, id36);
    }

    /**
     * 是否為「周轉池」：Site#24 / Site#35
     */
    private boolean isCoverPoolSite(Long locId) {
        if (locId == null) return false;
        Long id24 = getLocationId("Site#24").orElse(null);
        Long id35 = getLocationId("Site#35").orElse(null);
        return Objects.equals(locId, id24) || Objects.equals(locId, id35);
    }

    // ======================= 在 PICK 時：依「是否露出」決定是否建異物檢 =======================

    /**
     * 這次 PICK 完是否會讓來源頂層露出『一般片』
     */
    private boolean willExposeProductAfterPick(LayerBreakdown srcBefore, int leaveLayers) {
        if (leaveLayers <= 0) return false; // 全搬空，無下一層
        int totalBefore = Math.max(0, srcBefore.total());
        int moved = Math.max(0, totalBefore - leaveLayers);
        int coversBefore = Math.max(0, srcBefore.topCover) + Math.max(0, srcBefore.workerCover);
        int coversAfter = Math.max(0, coversBefore - moved);
        // 有一般片 & 搬完後頂層無蓋 → 露出
        return (Math.max(0, srcBefore.normal) > 0) && (coversAfter == 0);
    }

    /**
     * 若會露出一般片且目前該夾爪沒有進行中 job → 建立 inspection_job（containerMainId 指向 moving），並同時綁 moving/left。
     *
     * @return true 表示本次有新建單（已綁定）；false 表示未新建（可能已有 job 或資料不足）
     */
    private boolean maybeCreateInspectionJobAndBind(Long gripperId,
                                                    Long movingId,
                                                    Long leftId,
                                                    Long fromLocationId) {
        if (gripperId == null || movingId == null || leftId == null) return false;

        // 已有進行中就不重複建
        if (inspectionJobRepository.findActiveByGripper(gripperId).isPresent()) return false;

        try {
            return inspectionRouteMapRepository.findByGripperIdEnabled(gripperId).map(route -> {
                InspectionJob job = new InspectionJob();
                job.setJobKey(UUID.randomUUID().toString());
                job.setGripperId(gripperId);
                job.setContainerMainId(movingId);                 // ★ 以 moving 那顆為檢測對象
                job.setOriginSiteName(nameOfLocation(fromLocationId));
                job.setFirstStationId(route.getFirstStationId()); // 預期 VIRTUAL#6
                job.setSecondStationId(route.getSecondStationId());// 預期 VIRTUAL#7
                job.setCameraId(route.getCameraId());
                job.setStatus(J_WAIT_MOVE_FIRST);
                job.setIsClosed(false);
                job.setCreatedTime(LocalDateTime.now());
                job.setUpdatedTime(LocalDateTime.now());
                inspectionJobRepository.save(job);

                // 建單後立刻綁 moving/left（寫入 INSPECT_* attrs）
                linkToInspectionIfAny(gripperId, movingId, leftId);

                log.info("[InspectCreate] gripper#{} 建 inspection_job#{} (first={}, second={}, cam#{})",
                        gripperId, job.getId(), route.getFirstStationId(), route.getSecondStationId(), route.getCameraId());
                return true;
            }).orElseGet(() -> {
                log.warn("[InspectCreate] 找不到 gripper#{} 的異物檢路線（enabled）", gripperId);
                return false;
            });
        } catch (Exception e) {
            log.warn("[InspectCreate] 建立 inspection_job 失敗 g#{} m#{} l#{} : {}", gripperId, movingId, leftId, e.getMessage());
            return false;
        }
    }

    private String nameOfLocation(Long locId) {
        return (locId == null) ? null :
                locationPointRepository.findById(locId).map(LocationPoint::getName).orElse(null);
    }

    // ======================= 在 PICK 時綁 moving/left 與當前 inspection job =======================
    private void linkToInspectionIfAny(Long gripperId, Long movingId, Long leftId) {
        if (gripperId == null || movingId == null || leftId == null) return;

        inspectionJobRepository.findActiveByGripper(gripperId).ifPresent(job -> {
            String st = job.getStatus();
            // 只在異物檢流程進行中時綁，避免誤綁舊單
            if (!List.of("CREATED", "WAIT_MOVE_FIRST", "MOVING_FIRST", "FIRST_DONE", "WAIT_MOVE_SECOND", "MOVING_SECOND").contains(st)) {
                return;
            }
            String jobIdStr = String.valueOf(job.getId());
            String ts = String.valueOf(System.currentTimeMillis());
            String grip = String.valueOf(gripperId);

            // MOVING 標記（夾走的那顆 = srcIdOrMovingNewId）
            upsertAttr(movingId, ATTR_INSPECT_JOB_ID, jobIdStr);
            upsertAttr(movingId, ATTR_INSPECT_ROLE, ROLE_MOVING);
            upsertAttr(movingId, ATTR_INSPECT_BIND_TS, ts);
            upsertAttr(movingId, ATTR_INSPECT_BIND_GRIP, grip);

            // LEFT 標記（留下的那顆）
            upsertAttr(leftId, ATTR_INSPECT_JOB_ID, jobIdStr);
            upsertAttr(leftId, ATTR_INSPECT_ROLE, ROLE_LEFT);
            upsertAttr(leftId, ATTR_INSPECT_BIND_TS, ts);
            upsertAttr(leftId, ATTR_INSPECT_BIND_GRIP, grip);

            log.info("[InspectLink] job#{} 綁定 moving#{} / left#{}（g#{}）", job.getId(), movingId, leftId, gripperId);
        });
    }

    // ======================= VIRTUAL#12/13/14 轉換工具 =======================

    /**
     * 將可能落在 VIRTUAL#12/13/14 的目標，正規化成 Transfer#8 的 locationId；否則原值返回。
     */
    private Long normalizeDropTarget(Long toLocationId) {
        if (toLocationId == null) return null;
        ensureDropMappingCached();

        // 快取若完成且命中 → 導向 Transfer#8
        if (cachedVirtualToT8Ids != null && cachedTransfer8Id != null && cachedVirtualToT8Ids.contains(toLocationId)) {
            return cachedTransfer8Id;
        }
        // 沒命中或快取不完整 → 直接回原值（避免中斷流程）
        return toLocationId;
    }

    /**
     * 懶載入快取：把 VIRTUAL#12/13/14 與 Transfer#8 的名稱解析成 id 並快取。
     */
    private void ensureDropMappingCached() {
        // 已建好就不再解析
        if (cachedVirtualToT8Ids != null && cachedTransfer8Id != null) return;

        synchronized (this) {
            if (cachedVirtualToT8Ids != null && cachedTransfer8Id != null) return;

            // 找出 Transfer#8
            Optional<Long> t8 = getLocationId(TRANSFER8_NAME);
            if (t8.isEmpty()) {
                log.warn("[DropNormalize] 找不到目標站點 '{}'; 將跳過 VIRTUAL→Transfer#8 對應。", TRANSFER8_NAME);
                // 標記為已初始化（避免每次重試），但讓 cachedTransfer8Id 保持 null 以便安全跳過
                cachedVirtualToT8Ids = Collections.emptySet();
                return;
            }
            Long t8Id = t8.get();

            // 收集三個 VIRTUAL 的 id
            Set<Long> vIds = new HashSet<>();
            for (String name : VIRTUAL_TO_T8_NAMES) {
                getLocationId(name).ifPresentOrElse(
                        lp -> vIds.add(lp),
                        () -> log.warn("[DropNormalize] 找不到來源虛擬站點 '{}'，本次將無法對應此站。", name)
                );
            }

            if (vIds.isEmpty()) {
                log.warn("[DropNormalize] VIRTUAL#12/13/14 皆未解析到 id；將不做對應。");
                cachedVirtualToT8Ids = Collections.emptySet();
                cachedTransfer8Id = t8Id; // 留著，以後如果補齊 VIRTUAL 名稱也能用
            } else {
                cachedVirtualToT8Ids = Collections.unmodifiableSet(vIds);
                cachedTransfer8Id = t8Id;
                log.info("[DropNormalize] 建立對應：{} → {} (ids={} → {})",
                        VIRTUAL_TO_T8_NAMES, TRANSFER8_NAME, vIds, t8Id);
            }
        }
    }

    private Optional<Long> getLocationId(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(LocationIdCache.computeIfAbsent(name, key ->
                locationPointRepository.findByName(key)
                        .map(LocationPoint::getId)
                        .orElse(null)));
    }

    private Optional<ContainerAttr> findAttr(Long containerId, String attrKey) {
        if (containerId == null || attrKey == null) {
            return Optional.empty();
        }
        TaskContext ctx = taskContext();
        AttrKey key = new AttrKey(containerId, attrKey);
        Optional<ContainerAttr> cached = ctx.attrCache.get(key);
        if (cached != null) {
            return cached;
        }
        loadContainerAttrs(containerId);
        Optional<ContainerAttr> result =
                ctx.attrCache.getOrDefault(
                        key,
                        Optional.empty()
                );
        // ★ 把 miss 也記進 cache
        ctx.attrCache.putIfAbsent(key, result);
        return result;
    }

    private void loadContainerAttrs(Long containerId) {
        TaskContext ctx = taskContext();
        Map<String, ContainerAttr> attrs = containerAttrRepository.findContainerAttrs(containerId);
        if (attrs.isEmpty()) {
            return;
        }
        for (ContainerAttr attr : attrs.values()) {
            AttrKey key = new AttrKey(
                    attr.getContainerMainId(),
                    attr.getAttrKey()
            );
            ctx.attrCache.put(
                    key,
                    Optional.of(attr)
            );
        }
    }

    private TaskContext taskContext() {
        TaskContext ctx = taskContextHolder.get();
        if (ctx == null) {
            ctx = new TaskContext();
            taskContextHolder.set(ctx);
        }
        return ctx;
    }

    private class TaskContext {
        private final Map<AttrKey, Optional<ContainerAttr>> attrCache = new HashMap<>();
        private final Map<Long, Optional<ContainerData>> containerDataCache = new HashMap<>();

        Optional<ContainerData> findContainerData(Long containerMainId) {
            if (containerMainId == null) return Optional.empty();
            return containerDataCache.computeIfAbsent(
                    containerMainId,
                    containerDataRepository::findByContainerMainId
            );
        }

        ContainerData readOrCreateContainerData(Long containerMainId) {
            Optional<ContainerData> cached = findContainerData(containerMainId);
            if (cached.isPresent()) {
                return cached.get();
            }

            ContainerData data = new ContainerData();
            data.setContainerMainId(containerMainId);
            data.setEstimatedQuantity(0);
            data.setWorkCoverLayers(0);
            data.setCoverLayers(0);
            data.setProductLayers(0);
            containerDataRepository.save(data);
            putContainerData(data);
            return data;
        }

        void putContainerData(ContainerData data) {
            if (data == null || data.getContainerMainId() == null) return;
            containerDataCache.put(data.getContainerMainId(), Optional.of(data));
        }
    }

    public record AttrKey(
            Long containerId,
            String attrKey
    ) {
    }
}
