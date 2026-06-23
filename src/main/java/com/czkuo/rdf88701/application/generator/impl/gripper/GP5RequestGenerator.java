package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import com.czkuo.rdf88701.infra.lock.InProcLocks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * GP5RequestGenerator（拆站規則 + 三路層別 + 36 空站整把抓）
 * <p>
 * 規則摘要：
 * 1) Site#35：首次進 35 必做紅外線量測（verified_quantity）後才允許搬運。
 * 2) Site#36：拆站，目標讓「一般片」維持 6 的倍數；不足自 35 補、超過回併至 35；各站上限 22 層（工蓋+上蓋+一般）。
 * 3) 任一 PICK 的來源必須「有蓋」：covers = 工蓋 + 上蓋 > 0。
 * 4) 上蓋只能在最上方，不得混入一般片；要搬 N 片一般片時，實務會一次搬 N+1 層（含頂部上蓋）。
 * 5) 夾爪持物時不再固定丟 36：會先做「借蓋鋪路」（如 36=2+1 → 丟到 35 讓 35=20+2），再進行拆補。
 * 6) 36 空站：
 * - 夾爪「只有蓋」時，不可丟 36；若 35 可放則先丟回 35 鋪路；否則維持夾持等待。
 * - 夾爪「含一般片」時，才可丟 36 開拆。
 * 7) 當今天有做到「拆」的動作（即執行 PICK），需要檢查：這次 PICK 完之後，來源站頂層是否會露出「一般片」。
 * - 判斷式：leaveLayers > 0 且 coversAfter == 0
 * 其中 moved = totalBefore - leaveLayers；coversAfter = max(coversBefore - moved, 0)
 * - 若會露出一般片 → 需要先做「異物檢」流程：
 * 先 MOVE 到 VIRTUAL#6，再 MOVE 到 VIRTUAL#7（兩段皆完成後，才回到原本的 DROP/拆站流程）。
 * - 若不會露出（例如 PICK 後頂層仍有上蓋，或整把搬空）→ 不需異物檢，照原邏輯執行。
 */
@Slf4j
@Component("GP5")
@RequiredArgsConstructor
public class GP5RequestGenerator implements GripperRequestGenerator {

    // ===== Repository / Cache 依賴 =====
    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;
    private final WorkingBeamRequestRepository workingBeamrequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;
    private final GripperStatusCache gripperStatusCache;

    private final RobotInR029Repository r029Repo;
    private final RobotInR029LotRepository r029LotRepo;
    private final ContainerMainRepository containerMainRepository;
    private final DeviceProcessStateReader stateReader;

    // 異物檢相關（路線、任務、站點）
    private final InspectionRouteMapRepository inspectionRouteMapRepository;
    private final InspectionJobRepository inspectionJobRepository;
    private final InspectionStationRepository inspectionStationRepository;

    // ===== 常數設定 =====
    private static final String SOURCE_NAME = "Site#35";
    private static final String TARGET_NAME = "Site#36";
    private static final Map<String, Long> LocationIdCache = new ConcurrentHashMap<>();
    private static final Map<Long, String> LocationNameCache = new ConcurrentHashMap<>();

    // 虛擬站點（此檔案只負責 MOVE；拍照由另一隻處理）
    private static final String VIRTUAL6_NAME = "VIRTUAL#6";
    private static final String VIRTUAL7_NAME = "VIRTUAL#7";

    @Value("${app.gripper.gp5.default-group-size:0}")
    private int DEFAULT_GROUP_SIZE;           // 找不到 R029 時的回退值
    private static final int MAX_PIECES = 22;  // 容量上限：工蓋+上蓋+一般
    private static final long INFRARED_ID = 4L;
    private static final long BEAM_ID = 6L;
    private static final long GripperId = 5L;
    private static final int SOURCE_LEVEL = 35;
    @SuppressWarnings("unused")
    private static final int TARGET_LEVEL = 36;

    // content_kind（缺欄位時的保守推導）
    private static final String KIND_UNKNOWN = "UNKNOWN";
    private static final String KIND_NORMAL_WITH_COVER = "NORMAL_WITH_COVER";
    private static final String KIND_NORMAL_NO_COVER = "NORMAL_NO_COVER";
    private static final String KIND_ALL_COVER = "ALL_COVER";
    private static final String KIND_EMPTY = "EMPTY";

    // 異物檢任務狀態常數（與相機那隻協作）
    private static final String J_CREATED = "CREATED";
    private static final String J_WAIT_MOVE_FIRST = "WAIT_MOVE_FIRST";
    private static final String J_MOVING_FIRST = "MOVING_FIRST";
    private static final String J_FIRST_DONE = "FIRST_DONE";
    private static final String J_WAIT_MOVE_SECOND = "WAIT_MOVE_SECOND";
    private static final String J_MOVING_SECOND = "MOVING_SECOND";
    private static final String J_SECOND_DONE = "SECOND_DONE";
    private static final String J_DONE = "DONE";
    private static final String J_FAILED = "FAILED";

    // === R029 屬性鍵（穩定錨點）===
    private static final String ATTR_R029_COUNT = "R029_COUNT";   // = groupSize
    private static final String ATTR_R029_LOG_ID = "R029_LOG_ID";  // 指向 robot_in_r029.log_id

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // ---------------------------------------------------------------------
        // [0] 互斥檢查：避免與 IR / Gripper 彼此撞單
        // ---------------------------------------------------------------------
        if (!deviceIsRun("拆併區"))
            return Optional.empty();

        GripperGenerationContext generationContext = generationContext();
        if (generationContext.infraredBusy(INFRARED_ID) || generationContext.gripperBusy(GripperId)) {
            //log.debug("[GP5] 忙碌互斥（IR/Gripper/Beam），略過。");
            return Optional.empty();
        }
        String gn = "Gripper#" + GripperId;
        GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);
        boolean fresh = (ds != null) && ds.isValidAndComplete(5);
        if (!fresh) {
            //log.debug("[GP5] 夾爪狀態快取無效，略過本輪。");
            return Optional.empty();
        }

        GP5Context ctx = new GP5Context();
        Optional<Long> gp5Opt = ctx.getContainerIdByDevice(GripperId);
        // 目標 groupSize（優先以手上的 lot 對應 R029，其次 36，再來 35，否則預設）
        //int groupSize = resolveGroupSize(gp5Opt.orElse(null), c36Opt.orElse(null), c35Opt.orElse(null));
        int groupSize = ctx.groupSizeOpt();
        if (groupSize <= 0) {
            //log.debug("[GP5] 等待資料庫寫入 lot 對應 R029 的資訊。");
            return Optional.empty();
        }

        // ---------------------------------------------------------------------
        // [A] 夾爪「手上有帳」：優先處理「進行中的異物檢任務」的 MOVE（避免搶任務）
        // ---------------------------------------------------------------------
        // Optional<Long> heldOpt = locationTrackingRepository.findContainerOnGripper(GripperId);
        Optional<Long> result;
        if (gp5Opt.isPresent()) {
            return processHandRule(ctx);
        }
        // ---------------------------------------------------------------------
        // [B] 夾爪空手：先處理 35 的首次量測，再執行拆站邏輯
        // ---------------------------------------------------------------------
        Optional<Long> c35Opt = ctx.getContainerIdBySite(SOURCE_NAME);
        if (c35Opt.isPresent()) {
            Long c35 = c35Opt.orElse(null);
            if (!hasVerifiedQuantity(c35, ctx)) {
                Integer level = safeGetLevel(ds);
                if (level == null || (Objects.equals(ds.getGripperStatus().getWorkingStatusText().toUpperCase(), "IDLE") && level != SOURCE_LEVEL)) {
                    log.info("[GP5] 量測前需夾爪到位：目前Level={}, 目標Site#{}", level, SOURCE_LEVEL);
                    return createMoveTo(GripperId, SOURCE_NAME);
                }
                if (!generationContext.infraredBusy(INFRARED_ID)) {
                    triggerInfraredMeasure(INFRARED_ID, c35);
                    log.info("[GP5] 已向 Infrared#{} 送出量測請求，container#{}", INFRARED_ID, c35);
                }
                return Optional.empty();
            }
        }

        // ---------------------------------------------------------------------
        // [C] 夾爪空手：拆站邏輯
        // ---------------------------------------------------------------------
        // 站況（以三路層別讀值；欄位為 NULL 時用 kind+verified 推導）
        result = processSplitMerge(ctx);
        return result;

    }

    private Optional<Long> processHandRule(GP5Context ctx) {
        // A.1 若有進行中的 inspection_job → 依任務狀態排 MOVE 到 FIRST/SECOND
        Optional<InspectionJob> jobOpt = inspectionJobRepository.findActiveByGripper(GripperId);
        if (jobOpt.isPresent()) {
            // 已建立 MOVE，當輪不再下拆站單，避免搶
            // 沒建立 MOVE（例如：PLC 非 IDLE、已在目標站等待拍照、正在 MOVING_*）→ 本輪不動
            return handleInspectionMoveInsideHeld(GripperId, jobOpt.get());
        }

        // A.2 沒有 inspection_job → 照舊拆站判斷（借蓋、補齊、DROP 36 開拆）
        Optional<Long> c35Opt = ctx.getContainerIdBySite(SOURCE_NAME);
        Optional<Long> c36Opt = ctx.getContainerIdBySite(TARGET_NAME);
        Long gp5CId = ctx.getContainerIdByDevice(GripperId).orElse(null);
        Integer groupSize = ctx.groupSizeOpt();
        Counts s35 = ctx.getCountsByCid(c35Opt.orElse(null));
        Counts s36 = ctx.getCountsByCid(c36Opt.orElse(null));
        Counts gp5 = ctx.getCountsByCid(gp5CId);

        boolean canDrop35 = s35.total() < MAX_PIECES;
        boolean canDrop36 = s36.total() < MAX_PIECES;
        boolean site36HasContainer = c36Opt.isPresent();

        // 36 空站：
        if (!site36HasContainer) {
            // 夾爪只有蓋 → 不丟 36；若 35 可放則先丟回 35 鋪路
            if (gp5.product == 0 && gp5.covers() > 0) {
                if (c35Opt.isPresent() && canDrop35) {
                    int preTarget = s35.total();
                    log.info("[GP5] held=only covers → DROP 回 {}（鋪路），preTarget={}", SOURCE_NAME, preTarget);
                    return dropTo(GripperId, SOURCE_NAME, gp5CId, preTarget);
                } else {
                    log.info("[GP5] held=only covers 且 {} 無容器或滿載 → 維持夾持等待", SOURCE_NAME);
                    return Optional.empty();
                }
            }
            // 夾爪含一般片 → DROP 到 36 開拆
            if (gp5.product > 0 && canDrop36) {
                int preTarget = s36.total(); // 0
                log.info("[GP5] held 含 product，{} 空站 → DROP 到 {} 開拆", TARGET_NAME, TARGET_NAME);
                return dropTo(GripperId, TARGET_NAME, gp5CId, preTarget);
            }
            //log.debug("[GP5] {} 空站但條件不符（held 無 product 或容量不足），略過。", TARGET_NAME);
            return Optional.empty();
        }

        // 36 有容器：不足 groupSize 且無上蓋 → 若 held 有 product，DROP 到 36
        if (s36.product < groupSize && s36.cover < 1 && canDrop36 && gp5.product > 0) {
            log.info("[GP5] 夾爪持物 → {} 不足 {} 且無上蓋，DROP 到 {}", TARGET_NAME, groupSize, TARGET_NAME);
            return dropTo(GripperId, TARGET_NAME, gp5CId, s36.total());
        }

        // 借蓋鋪路：36 一般片在 (0..groupSize]，35 蓋 ≤ 1，且 held 無 product → 把蓋丟回 35
        if (s36.product <= groupSize
                && s35.covers() <= 1 && s35.product > 0
                && canDrop35 && gp5.product == 0) {
            int preTarget = s35.total();
            log.info("[GP5] 夾爪持物 → 借蓋鋪路：DROP 到 {}（讓 {} 有 2 蓋）", SOURCE_NAME, SOURCE_NAME);
            return dropTo(GripperId, SOURCE_NAME, gp5CId, preTarget);
        }

        //log.debug("[GP5] 夾爪持物但無合適 DROP 目標，略過本輪。");
        return Optional.empty();
    }

    private Optional<Long> processSplitMerge(GP5Context ctx) {
        int groupSize = ctx.groupSizeOpt();
        // 36 空站：35 有「一般片且有蓋」→ 整把抓到 36
        Optional<Long> c35Opt = ctx.getContainerIdBySite(SOURCE_NAME);
        Optional<Long> c36Opt = ctx.getContainerIdBySite(TARGET_NAME);
        Counts s35 = ctx.getCountsByCid(c35Opt.orElse(null));
        if (c36Opt.isEmpty()) {
            if (c35Opt.isPresent() && s35.product > 0 && s35.covers() > 0) {
                return pickLayers(
                        GripperId,
                        SOURCE_NAME,
                        TARGET_NAME,
                        c35Opt.get(),
                        /*leaveLayers*/ 0,
                        TARGET_NAME + " 空站 → 整把移至拆站（保留併批可能）",
                        s35
                );
            }
            //log.debug("[GP5] {} 空站且 {} 不具『一般片且有蓋』條件，略過。", TARGET_NAME, SOURCE_NAME);
            return Optional.empty();
        }

        // 36 有容器：讓一般片維持 groupSize 倍數
        Counts s36 = ctx.getCountsByCid(c36Opt.orElse(null));
        int p36 = s36.product;
        int p35 = s35.product;
        int c35covers = s35.covers();
        int c36covers = s36.covers();

        // 不足 groupSize：自 35 補
        if (p36 < groupSize) {
            // 來源無一般片 → 不動
            if (c35Opt.isEmpty() || p35 == 0) {
                //log.debug("[GP5] {} 不足 {} 但 {} 無一般片可補，暫不動。", TARGET_NAME, groupSize, SOURCE_NAME);
                return Optional.empty();
            }

            // Case 1：36 無蓋且已有一般片，35 有 ≥1 蓋 → 搬「所有上蓋 + 需要的一般（容量內）」
            if (c36covers == 0 && p36 > 0 && c35covers >= 1) {
                int topCoversAt35 = s35.covers();
                int needProducts = groupSize - p36;
                int maxFrom35 = Math.min(needProducts, p35);

                if (maxFrom35 > 0) {
                    int allow = MAX_PIECES - s36.total();
                    if (allow >= topCoversAt35 + 1) {
                        int moveProductsAdj = Math.min(maxFrom35, allow - topCoversAt35);
                        int layers = topCoversAt35 + moveProductsAdj;
                        int remain = s35.total() - layers;
                        if (moveProductsAdj >= 1 && remain >= 0) {
                            return pickLayers(
                                    GripperId,
                                    SOURCE_NAME,
                                    TARGET_NAME,
                                    c35Opt.get(),
                                    /*leaveLayers*/ remain,
                                    TARGET_NAME + " 無蓋且已有 product → 從 " + SOURCE_NAME + " 搬『所有上蓋 + 需要的一般（容量內）』",
                                    s35
                            );
                        }
                    } else {
                        //log.debug("[GP5] {} 容量不足（allow={} < covers({})+1），暫不搬。", TARGET_NAME, allow, topCoversAt35);
                    }
                }
            }

            // Case 2：36 有蓋且 35 有貨 → 先把 36 的蓋搬回 35（借蓋）
            if (c36covers >= 1 && p35 >= 1) {
                int remain = s36.total() - c36covers;
                if (remain >= 0) {
                    return pickLayers(
                            GripperId,
                            TARGET_NAME,
                            SOURCE_NAME,
                            c36Opt.get(),
                            /*leaveLayers*/ remain,
                            "借蓋鋪路：先把 " + TARGET_NAME + " 的蓋搬回 " + SOURCE_NAME,
                            s36
                    );
                }
            }

            // Case 3：來源有蓋 → (need + 1)
            if (c35covers > 0) {
                int needProducts = groupSize - p36;
                int moveProducts = Math.min(needProducts, p35);
                int layers = moveProducts + 1; // 一般 + 1 蓋
                int allow = MAX_PIECES - s36.total();
                layers = Math.min(layers, allow);
                int remain = s35.total() - layers;
                if (layers >= 2 && remain >= 0) {
                    return pickLayers(
                            GripperId,
                            SOURCE_NAME,
                            TARGET_NAME,
                            c35Opt.get(),
                            /*leaveLayers*/ remain,
                            TARGET_NAME + " 不足 " + groupSize + " → (need+1)",
                            s35
                    );
                }
                //log.debug("[GP5] {} 不足 {} 但容量受限，暫不動。", TARGET_NAME, groupSize);
                return Optional.empty();
            }

            //log.debug("[GP5] {} 不足 {} 但 {} 無蓋，等待借蓋。", TARGET_NAME, groupSize, SOURCE_NAME);
            return Optional.empty();
        }

        // 超過 groupSize：回併（在 36 留 groupSize；layerCount=留下量）
        if (p36 > groupSize) {
            if (s36.covers() == 0) {
                //log.debug("[GP5] {} 超過 {} 但來源無蓋，等待借蓋。", TARGET_NAME, groupSize);
                return Optional.empty();
            }
            int remain = Math.max(groupSize, 0);
            return pickLayers(
                    GripperId,
                    TARGET_NAME,
                    null,
                    c36Opt.get(),
                    /*leaveLayers*/ remain,
                    TARGET_NAME + " 超過 " + groupSize + " → 留 (remain) 至 " + TARGET_NAME,
                    s36
            );
        }

        // 等於 groupSize：若 36 蓋>1 且 35 無蓋 → 借回 1 蓋到 35
        if (p36 == groupSize) {
            if (s36.covers() > 1 && s35.covers() == 0 && c35Opt.isPresent()) {
                int allowAt35 = MAX_PIECES - s35.total();
                int remain = s36.total() - 1; // 36 留下一片蓋
                if (allowAt35 >= 1) {
                    return pickLayers(
                            GripperId,
                            TARGET_NAME,
                            SOURCE_NAME,
                            c36Opt.get(),
                            /*leaveLayers*/ remain,
                            TARGET_NAME + " 蓋多、" + SOURCE_NAME + " 無蓋 → 借回 1 蓋",
                            s36
                    );
                }
            }
            //log.debug("[GP5] {} 已齊 {}，暫不動（等待後段取走或下輪開新組）。", TARGET_NAME, groupSize);
            return Optional.empty();
        }
        return Optional.empty();
    }
    // ====================== 異物檢（僅在 held==true 時決定 MOVE） ======================

    /**
     * 夾爪手上有帳時，若存在進行中的 inspection_job，依狀態排 MOVE。
     * - CREATED / WAIT_MOVE_FIRST  → MOVE → firstStation，並標記 MOVING_FIRST
     * - FIRST_DONE / WAIT_MOVE_SECOND → MOVE → secondStation，並標記 MOVING_SECOND
     * - 其他（MOVING_* / SECOND_DONE / DONE / FAILED）→ 不動（避免搶任務）
     */
    private Optional<Long> handleInspectionMoveInsideHeld(Long gripperId, InspectionJob job) {
        String st = job.getStatus();
        if (J_CREATED.equals(st) || J_WAIT_MOVE_FIRST.equals(st)) {
            return moveToStationIfNotThere(gripperId, job.getFirstStationId(), J_MOVING_FIRST, "INSPECT FIRST", job);
        }
        if (J_FIRST_DONE.equals(st) || J_WAIT_MOVE_SECOND.equals(st)) {
            return moveToStationIfNotThere(gripperId, job.getSecondStationId(), J_MOVING_SECOND, "INSPECT SECOND", job);
        }
        //log.debug("[GP5] Inspect job#{} status={} → 不建 MOVE（等待相機流程）", job.getId(), st);
        return Optional.empty();
    }

    /**
     * 若夾爪尚未在 stationId 上，則建 MOVE；並把 job 標記為 nextStatus。
     * 判位使用 PLC level：target level 由 LocationPoint 名稱解析（如 VIRTUAL#6 → 6）。
     */
    private Optional<Long> moveToStationIfNotThere(Long gripperId, Long stationId, String nextStatus, String tag, InspectionJob job) {
        if (stationId == null) {
            log.warn("[GP5] stationId 為 null，無法 MOVE（{}）", tag);
            return Optional.empty();
        }

        Optional<Integer> targetLevelOpt = stationLevel(stationId);
        if (targetLevelOpt.isEmpty()) {
            log.warn("[GP5] stationId={} 找不到 target level，無法 MOVE（{}）", stationId, tag);
            return Optional.empty();
        }
        int targetLevel = targetLevelOpt.get();

        // 讀 PLC 狀態（鮮度 3 秒）
        String gripperName = "Gripper#" + gripperId;
        GripperDeviceStatus ds = gripperStatusCache.getLatest(gripperName);
        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP5] MOVE 前快取無效（{}），略過", tag);
            return Optional.empty();
        }
        Integer currLevel = safeGetLevel(ds);
        String working = (ds.getGripperStatus() != null) ? ds.getGripperStatus().getWorkingStatusText() : null;

        // 已在目標站，就不送 MOVE
        if (currLevel != null && currLevel == targetLevel) {
            log.info("[GP5] 已在目標站 level={}，不重複 MOVE（{}）", currLevel, tag);
            return Optional.empty();
        }

        // 非 IDLE（可能 MOVING/忙碌），避免搶
        if (working != null && !"IDLE".equalsIgnoreCase(working)) {
            //log.debug("[GP5] 非 IDLE（{}），不送 MOVE（{}）", working, tag);
            return Optional.empty();
        }

        // 站點名稱 → 建 MOVE
        Optional<String> targetNameOpt = stationNameByStationId(stationId);
        if (targetNameOpt.isEmpty()) {
            log.warn("[GP5] stationId={} 找不到對應站名，無法 MOVE（{}）", stationId, tag);
            return Optional.empty();
        }

        Optional<Long> reqId = createMoveTo(gripperId, targetNameOpt.get());
        reqId.ifPresent(id -> {
            Long jobId = job.getId();
            if (jobId != null) {
                inspectionJobRepository.markStatus(jobId, nextStatus);
            }
            log.info("[GP5] 建 MOVE→{} 成功，job 標記 {}", targetNameOpt.get(), nextStatus);
        });
        return reqId;
    }

    /**
     * 透過 inspection_station → location_point 取得站名
     */
    private Optional<String> stationNameByStationId(Long stationId) {
        return Optional.ofNullable(getStationNameById(stationId));
//        return inspectionStationRepository.findById(stationId)
//                .flatMap(st -> locationPointRepository.findById(st.getLocationPointId()))
//                .map(LocationPoint::getName);
    }

    /**
     * 取得站點的 PLC level：由 location_point 名稱解析（不需要 inspection_station.plc_level）
     */
    private Optional<Integer> stationLevel(Long stationId) {
        return stationNameByStationId(stationId).flatMap(this::parseLevelFromLocationName);
    }

    /**
     * 嘗試由 "VIRTUAL#6"、"Site#35" 這類名稱解析出數字 level（# 後的連續數字；若無則取字串中的第一段數字）
     */
    private Optional<Integer> parseLevelFromLocationName(String name) {
        if (name == null) return Optional.empty();

        int idx = name.lastIndexOf('#');
        if (idx >= 0 && idx + 1 < name.length()) {
            String tail = name.substring(idx + 1).trim();
            int end = 0;
            while (end < tail.length() && Character.isDigit(tail.charAt(end))) end++;
            if (end > 0) {
                try {
                    return Optional.of(Integer.parseInt(tail.substring(0, end)));
                } catch (NumberFormatException ignore) { /* fallthrough */ }
            }
        }
        // 後備：抓名稱中的第一段數字
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        if (sb.length() > 0) {
            try {
                return Optional.of(Integer.parseInt(sb.toString()));
            } catch (NumberFormatException ignore) { /* noop */ }
        }
        return Optional.empty();
    }

    private Long getSafeJobIdByGripper(Long gripperId) {
        return inspectionJobRepository.findActiveByGripper(gripperId)
                .map(InspectionJob::getId)
                .orElse(null);
    }

    // ====================== 動態 GROUP_SIZE（取 R029 的 count） ======================

    /**
     * 以手上的優先，其次 「Site#36」，再來 「Site#35」取得 groupSize。
     * 順序：R029_COUNT(attr) → R029_LOG_ID(attr → robot_in_r029.count) → (fallback) lot 映射 → DEFAULT_GROUP_SIZE。
     */
    private int resolveGroupSize(Long heldId, Long c36Id, Long c35Id) {
        for (Long cmId : new Long[]{heldId, c36Id, c35Id}) {
            if (cmId == null) continue;

            // 1) 直接讀 attr.R029_COUNT
            Optional<Integer> byAttrCount = readIntAttr(cmId, ATTR_R029_COUNT);
            if (byAttrCount.isPresent() && byAttrCount.get() > 0) {
                return byAttrCount.get();
            }

            // 2) 讀 attr.R029_LOG_ID → robot_in_r029.count
            Optional<Long> logIdOpt = readLongAttr(cmId, ATTR_R029_LOG_ID);
            if (logIdOpt.isPresent()) {
                Optional<Integer> byLog = findGroupSizeByLogId(logIdOpt.get());
                if (byLog.isPresent()) return byLog.get();
            }
        }
        return DEFAULT_GROUP_SIZE;
    }

    private Optional<Integer> readIntAttr(Long cmId, String key) {
        try {
            return containerAttrRepository.findOne(cmId, key)
                    .map(ContainerAttr::getAttrValue)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::valueOf)
                    .filter(v -> v > 0);
        } catch (Exception e) {
            log.warn("[GP5] readIntAttr(cm#{}, {}) 解析失敗: {}", cmId, key, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Long> readLongAttr(Long cmId, String key) {
        try {
            return containerAttrRepository.findOne(cmId, key)
                    .map(ContainerAttr::getAttrValue)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(v -> {
                        try {
                            return Long.parseLong(v);
                        } catch (Exception ex) {
                            return null;
                        }
                    });
        } catch (Exception e) {
            log.warn("[GP5] readLongAttr(cm#{}, {}) 解析失敗: {}", cmId, key, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Integer> findGroupSizeByLogId(Long logId) {
        if (logId == null) return Optional.empty();
        return r029Repo.findById(logId)
                .map(RobotInR029::getCount)
                .filter(c -> c != null && c > 0);
    }

    // ====================== 取數 + 保守推導 ======================

    /**
     * 取得某容器的三路層數（工蓋/上蓋/一般）。
     * 欄位為 NULL 時，改以 content_kind + estimatedQuantity 做保守推導：
     * - ALL_COVER         → 工蓋=0，上蓋=estimated，一般=0
     * - NORMAL_NO_COVER   → 工蓋=0，上蓋=0，一般=estimated
     * - EMPTY             → 0,0,0
     * - 其他(UNKNOWN/NORMAL_WITH_COVER/NULL)
     * → 工蓋=0，上蓋=(estimated>0 ? 1 : 0)，一般=max(estimated-上蓋,0)
     */
    private Counts countsAt(Long containerMainId, GP5Context ctx) {
        if (containerMainId == null) {
            return new Counts(0, 0, 0);
        }
        // ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        ContainerData cd = ctx.getContainerDataByCid(containerMainId);
        if (cd == null)
            return new Counts(0, 0, 0);

        Integer w = cd.getWorkCoverLayers();
        Integer c = cd.getCoverLayers();
        Integer p = cd.getProductLayers();

        if (w == null || c == null || p == null) {
            int estimated = cd.getEstimatedQuantity() == null ? 0 : cd.getEstimatedQuantity();
            String kind = cd.getContentKind();

            if (KIND_ALL_COVER.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = estimated;
                if (p == null) p = 0;
            } else if (KIND_NORMAL_NO_COVER.equals(kind) || KIND_EMPTY.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = 0;
                if (p == null) p = estimated;
            } else {
                int cover = (estimated > 0 ? 1 : 0);
                if (w == null) w = 0;
                if (c == null) c = cover;
                if (p == null) p = Math.max(estimated - cover, 0);
            }
        }

        int wi = Math.max(0, w == null ? 0 : w);
        int ci = Math.max(0, c == null ? 0 : c);
        int pi = Math.max(0, p == null ? 0 : p);
        return new Counts(wi, ci, pi);
    }

    // ====================== 共同 I/O（建請求） ======================

    /**
     * 產生 PICK；來源必須有蓋（工蓋+上蓋>0）。參數 leaveLayers = layerCount(留下量)。
     */
    private Optional<Long> pickLayers(Long gripperId, String srcName, String tgtName,
                                      Long containerMainId, int leaveLayers, String reason, Counts src) {
        if (src.covers() <= 0) {
            //log.debug("[GP5] 來源 {} 無蓋（工蓋+上蓋=0），禁止 PICK：{}", srcName, reason);
            return Optional.empty();
        }

        int safeLeave = Math.max(0, Math.min(leaveLayers, MAX_PIECES));
        log.info("[GP5] 建立 PICK：{} -> {}，leave(layerCount)={}，reason={}",
                srcName, tgtName, safeLeave, reason);
        return createRequest(gripperId, "PICK", srcName, tgtName, containerMainId, safeLeave);
    }

    /**
     * 便利：DROP 到目標站；preTargetLayers = 落料前目標站「當下總層數」（layerCount）。
     */
    private Optional<Long> dropTo(Long gripperId, String targetName, Long containerMainId, int preTargetLayers) {
        int safe = Math.max(0, Math.min(preTargetLayers, MAX_PIECES));
        return createRequest(gripperId, "DROP", null, targetName, containerMainId, safe);
    }

    private GripperGenerationContext generationContext() {
        return new GripperGenerationContext(
                requestRepository,
                taskRepository,
                infraredRequestRepository,
                infraredTaskRepository,
                workingBeamrequestRepository,
                workingBeamTaskRepository
        );
    }

    /**
     * Gripper 是否忙碌（有未完成請求或任務）
     */
    private boolean gripperBusy(Long gripperId) {
        return requestRepository.existsUnfinishedRequestForDevice(gripperId)
                || taskRepository.existsUnfinishedTaskForGripper(gripperId);
    }

    /**
     * 指定紅外線裝置是否忙碌（有未完成請求或任務）
     */
    private boolean infraredBusy(long infraredId) {
        return infraredRequestRepository.existsUnfinishedRequestForInfrared(infraredId)
                || infraredTaskRepository.existsUnfinishedTaskForInfrared(infraredId);
    }

    /**
     * 指定工作樑裝置是否忙碌（有未完成請求或任務）
     */
    private boolean workingBeamBusy(long workingBeamId) {
        return workingBeamrequestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || workingBeamTaskRepository.existsUnfinishedTaskForBeam(workingBeamId);
    }

    /**
     * 該容器 verified_quantity > 0 ?
     */
    private boolean hasVerifiedQuantity(Long containerMainId, GP5Context ctx) {
        return Optional.ofNullable(ctx.getContainerDataByCid(containerMainId))
                .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                .orElse(0) > 0;
    }

    /**
     * 送出紅外線量測請求
     */
    private void triggerInfraredMeasure(long infraredId, Long containerMainId) {
        if (infraredBusy(infraredId)) return;
        infraredRequestRepository.createMeasureRequestForContainer(containerMainId, infraredId);
    }

    /**
     * 從 PLC 狀態安全取得目前 Level
     */
    private Integer safeGetLevel(GripperDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    /**
     * 建立 MOVE → 指定站點（layerCount 固定 0，高度不下發）
     */
    private Optional<Long> createMoveTo(Long gripperId, String targetName) {
//        Long targetId = locationPointRepository.findByName(targetName)
//                .map(LocationPoint::getId)
//                .orElseThrow(() -> new IllegalArgumentException("Invalid target location: " + targetName));
        Long targetId = getLocationId(targetName);
        GripperRequest request = baseRequest(gripperId, "MOVE", null, targetId, null);
        request.setSourceLocationName(null);
        request.setTargetLocationName(targetName);
        request.setLayerCount(0);
        request.setTargetHeightMm(BigDecimal.ZERO);

        boolean ok = requestRepository.save(request);
        if (ok) {
            log.info("[GP5] 建立 MOVE 請求 → {}", targetName);
            return Optional.of(request.getId());
        }
        log.warn("[GP5] 建立 MOVE 請求失敗");
        return Optional.empty();
    }

    /**
     * 建立 GripperRequest（PICK/DROP）
     */
    private Optional<Long> createRequest(Long gripperId, String taskType, String source, String target,
                                         Long containerMainId, int layerCount) {
        // ── 只在「對 Site#36 的 PICK/DROP」時啟用互斥 ─────────────────────────
        final boolean isPick = "PICK".equalsIgnoreCase(taskType);
        final boolean isDrop = "DROP".equalsIgnoreCase(taskType);
        final boolean touches36 =
                (isPick && (TARGET_NAME.equals(source) || TARGET_NAME.equals(target)))  // PICK from/to 36
                        || (isDrop && TARGET_NAME.equals(target));                      // DROP to 36

        // 1. 持久 Busy（可跨重啟）：WB5 是否有未完成 request / task？
        if (touches36 && workingBeamBusy(BEAM_ID)) {
            //log.debug("[GP5] WB5 busy（unfinished 持久狀態）→ 擋下這次 {} 對 {}", taskType, TARGET_NAME);
            return Optional.empty();
        }

        // 2. 瞬間競態（同程序 200ms tick 內）：in-proc 鎖
        boolean locked = false;
        if (touches36) {
            locked = InProcLocks.tryEnterGp5Site36();
            if (!locked) {
                //log.debug("[GP5] in-proc 互斥：有別的流程正在操作 {} → 放棄這次 {}", TARGET_NAME, taskType);
                return Optional.empty();
            }
        }

        try {
            // ── 原本的建立流程 ───────────────────────────────────────────
//            Long sourceId = (source != null)
//                    ? locationPointRepository.findByName(source)
//                    .map(LocationPoint::getId)
//                    .orElseThrow(() -> new IllegalArgumentException("Invalid source location: " + source))
//                    : null;
            Long sourceId = getLocationId(source);
//            Long targetId = (target != null)
//                    ? locationPointRepository.findByName(target)
//                    .map(LocationPoint::getId)
//                    .orElseThrow(() -> new IllegalArgumentException("Invalid target location: " + target))
//                    : null;
            Long targetId = getLocationId(target);

            GripperRequest request = baseRequest(gripperId, taskType, sourceId, targetId, containerMainId);
            request.setSourceLocationName(source);
            request.setTargetLocationName(target);

            int safeLayers = Math.max(0, Math.min(layerCount, MAX_PIECES));
            request.setLayerCount(safeLayers);

            Double trayThickness = resolveTrayThicknessSafe(containerMainId);
            if (trayThickness == null) {
                return Optional.empty();
            }
            request.setTargetHeightMm(BigDecimal.valueOf(trayThickness));

            boolean ok = requestRepository.save(request);
            if (ok) {
                log.info("[GP5] 建立 GripperRequest 成功: {} -> {} [{}] containerId={} layerCount={}",
                        source, target, taskType, containerMainId, safeLayers);
                return Optional.of(request.getId());
            }
            log.warn("[GP5] 建立 GripperRequest 失敗 [{}]", taskType);
            return Optional.empty();

        } finally {
            if (touches36 && locked) {
                InProcLocks.exitGp5Site36();
            }
        }
    }

    /**
     * 共同欄位初始化
     */
    private GripperRequest baseRequest(Long gripperId, String taskType, Long sourceId, Long targetId, Long containerMainId) {
        GripperRequest request = new GripperRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setGripperId(gripperId);
        request.setTaskType(taskType); // "PICK" / "DROP" / "MOVE"
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        request.setSourceLocationId(sourceId);
        request.setTargetLocationId(targetId);
        request.setContainerMainId(containerMainId);
        return request;
    }

    /**
     * 三路層數封裝：工蓋 / 上蓋 / 一般
     */
    private record Counts(int workCover, int cover, int product) {
        int covers() {
            return workCover + cover;
        }

        int total() {
            return workCover + cover + product;
        }
    }

    /**
     * 讀取單片托盤厚度（mm）；來源 container_attr.key=tray_thickness_mm。格式寬鬆；錯誤回 null。
     */
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

    /**
     * 寬鬆數值解析並要求正數：允許 "5.62", "5,62", "5.62mm"；非正或格式不對回 null。
     */
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

    private Long getLocationId(String name) {
        if (name == null) return null;
        return LocationIdCache.computeIfAbsent(name, key ->
                locationPointRepository.findByName(key)
                        .map(LocationPoint::getId)
                        .orElseThrow(() ->
                                new IllegalArgumentException("Invalid source location: " + key))
        );
    }

    private String getStationNameById(Long stationId) {
        if (stationId == null) return null;
        return LocationNameCache.computeIfAbsent(stationId, key ->
                inspectionStationRepository.findById(stationId)
                        .flatMap(st -> locationPointRepository.findById(st.getLocationPointId()))
                        .map(LocationPoint::getName)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid source location: " + key)));
    }

    private class GP5Context {
        int groupSize = -1;
        Map<Long, ContainerData> containerCacheByCid = new ConcurrentHashMap<>();
        Map<String, Optional<Long>> containerIdCacheBySite = new ConcurrentHashMap<>();
        Map<Long, Optional<Long>> containerIdCacheByDevice = new ConcurrentHashMap<>();
        Map<Long, Counts> countsCacheByCId = new ConcurrentHashMap<>();

        ContainerData getContainerDataByCid(Long id) {
            if (containerCacheByCid == null) {
                containerCacheByCid = new ConcurrentHashMap<>();
            }
            if (id == null)
                return null;
            return containerCacheByCid.computeIfAbsent(id, key ->
                    containerDataRepository.findByContainerMainId(key).orElse(null));

        }

        Optional<Long> getContainerIdBySite(String name) {
            if (containerIdCacheBySite == null) {
                containerIdCacheBySite = new ConcurrentHashMap<>();
            }
            if (name == null)
                return Optional.empty();
            return containerIdCacheBySite.computeIfAbsent(name, locationTrackingRepository::findContainerAtLocationName);
        }

        Optional<Long> getContainerIdByDevice(Long id) {
            if (containerIdCacheByDevice == null) {
                containerIdCacheByDevice = new ConcurrentHashMap<>();
            }
            if (id == null)
                return Optional.empty();
            return containerIdCacheByDevice.computeIfAbsent(id, locationTrackingRepository::findContainerOnGripper);
        }

        Counts getCountsByCid(Long id) {
            if (countsCacheByCId == null) {
                countsCacheByCId = new ConcurrentHashMap<>();
            }
            if (id == null) {
                return countsAt(null, this);
            }
            return countsCacheByCId.computeIfAbsent(id, key -> countsAt(key, this)
            );
        }

        int groupSizeOpt() {
            if (groupSize == -1) {
                groupSize = resolveGroupSize(getContainerIdByDevice(GripperId).orElse(null)
                        , getContainerIdBySite(TARGET_NAME).orElse(null)
                        , getContainerIdBySite(SOURCE_NAME).orElse(null));
            }
            return groupSize;
        }

    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
