package com.czkuo.rdf88701.application.monitor.camera;

import com.czkuo.rdf88701.application.service.camera.CameraModbusService;
import com.czkuo.rdf88701.common.dto.camera.TwoCamerasSnapshot;
import com.czkuo.rdf88701.common.enums.camera.CameraState;
import com.czkuo.rdf88701.config.modbus.CameraModbusProperties;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.InspectionJob;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * CameraModbusMonitor
 * <p>
 * 核心職責（只做「拍照時機判斷 + 任務狀態推進」，不搶 MOVE 任務）：
 *   1) 每次 poll 讀兩台相機（Cam1、Cam2）的 Modbus 狀態。
 *   2) 查「該相機對應夾爪」目前是否有進行中的 InspectionJob（每支夾爪同時間只允許一筆未關閉）。
 *   3) 條件符合就觸發第一次 / 第二次拍照（trigger），條件包括：
 *        - 相機目前狀態（IDLE / FIRST_DONE_WAIT_SECOND / ...）
 *        - 夾爪是否「有帳」
 *        - 夾爪是否「到達指定虛擬站」（以 PLC 的 level 與 station 對應之 location_point.name 解析出來的 level 比對）
 *   4) 相機狀態回報後，推進 InspectionJob 狀態（FIRST_DONE, SECOND_DONE, DONE / FAILED）。
 * <p>
 * MOVE 的決策（包含把夾爪送到 V6→V7 或 V8→V9）由 GP4/GP5 的 Generator 控制，本類不涉及，以避免兩邊搶任務。
 * <p>
 * 快閃容錯（第二次拍照）：
 *   - 緣由：某些現場相機在觸發第二次拍後，狀態「SECOND_DONE_AUTO_TO_IDLE」很快回到 IDLE，
 *           可能在 poll 週期內錯失該瞬間。
 *   - 解法（記憶體版）：觸發第二次拍照前，先記住三個「基準值」：secondCount / total / times，
 *           若之後看到相機已回 IDLE，但任一計數 > 基準值，即可合理推斷第二次已完成 → 安全補關（SECOND_DONE → DONE）。
 * <p>
 * 本次補強（軟體重開容錯）：
 *   - 問題：baseline 存在記憶體 AtomicX，軟體重開會全部遺失，導致第二次快閃回 IDLE 後可能永遠補不到 DONE。
 *   - 解法：利用相機回傳的 times（每拍一次 +1；第一次/第二次都會各 +1）作為跨重開的「可持久錨點」：
 *       1) 第一次完成時，把當下 times 寫入 attr：INSPECT_FIRST_TIMES（moving/left 成對優先）
 *       2) 觸發第二次前，把 baseline times 寫入 attr：INSPECT_SECOND_BASE_TIMES（建議保留以利稽核 & 提高補救精準度）
 *       3) 第二次完成時，把當下 times 寫入 attr：INSPECT_SECOND_TIMES
 *   - 重開後補救策略：
 *       - 優先使用 SECOND_BASE_TIMES 作為 anchor（更精準：代表「準備觸發第二次那一刻」的 times）
 *       - 拿不到才退回使用 FIRST_TIMES
 * <p>
 * 並發 / 安全性：
 *   - 本類僅用 AtomicX 做狀態暫存（baseline + last state），避免同步成本。
 *   - 寫任務狀態時盡量 idempotent（多次 update 同一狀態不致出錯）。
 *   - Repository 層若可提供「原子更新」（例如 WHERE status!=target 或 WHERE is_closed=0），可進一步提升穩定性。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CameraModbusMonitor {

    // ====== 相機服務（封裝 Modbus 寫入/讀取）與設定 ======
    private final CameraModbusService svc;
    private final CameraModbusProperties props;

    // ====== 任務/站點/位置/夾爪狀態 ======
    private final InspectionJobRepository inspectionJobRepository;

    /**
     * 路線表：目前程式沒用到它（保留欄位可理解為未來要做「工藝/路由」時使用）。
     * 若短期確定不需要，可移除依賴，避免 IDE 顯示 unused。
     */
    private final InspectionRouteMapRepository inspectionRouteMapRepository;

    private final InspectionStationRepository inspectionStationRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final GripperStatusCache gripperStatusCache;

    // 寫入/查詢 attr（container_attr）
    private final ContainerAttrRepository containerAttrRepository;

    // 最近一次「相機狀態」快照（純記錄用：只拿來 log state 變化）
    private final AtomicReference<CameraState> lastC1 = new AtomicReference<>(CameraState.IDLE);
    private final AtomicReference<CameraState> lastC2 = new AtomicReference<>(CameraState.IDLE);

    // ====== 任務狀態常數（需與 GP4/GP5 的 Generator 保持一致） ======
    private static final String J_CREATED          = "CREATED";
    private static final String J_WAIT_MOVE_FIRST  = "WAIT_MOVE_FIRST";
    private static final String J_MOVING_FIRST     = "MOVING_FIRST";
    private static final String J_FIRST_DONE       = "FIRST_DONE";
    private static final String J_WAIT_MOVE_SECOND = "WAIT_MOVE_SECOND";
    private static final String J_MOVING_SECOND    = "MOVING_SECOND";
    private static final String J_SECOND_DONE      = "SECOND_DONE";
    private static final String J_DONE             = "DONE";
    private static final String J_FAILED           = "FAILED";

    // Cam1 對應 Gripper#4（VIRTUAL#6 / VIRTUAL#7），Cam2 對應 Gripper#5（VIRTUAL#8 / VIRTUAL#9）
    private static final long GRIPPER_ID_CAM1 = 4L;
    private static final long GRIPPER_ID_CAM2 = 5L;

    // ====== 「第二次拍照」快閃容錯追蹤（記憶體版 baseline，重開會遺失） ======
    private final AtomicLong secondJobCam1        = new AtomicLong(0L);
    private final AtomicInteger baseSecondCntCam1 = new AtomicInteger(0);
    private final AtomicInteger baseTotalCntCam1  = new AtomicInteger(0);
    private final AtomicInteger baseTimesCam1     = new AtomicInteger(0);

    private final AtomicLong secondJobCam2        = new AtomicLong(0L);
    private final AtomicInteger baseSecondCntCam2 = new AtomicInteger(0);
    private final AtomicInteger baseTotalCntCam2  = new AtomicInteger(0);
    private final AtomicInteger baseTimesCam2     = new AtomicInteger(0);

    // ====== Attr Keys（寫入 container_attr）======
    private static final String ATTR_INSPECT_FIRST_COUNT   = "INSPECT_FIRST_COUNT";
    private static final String ATTR_INSPECT_SECOND_COUNT  = "INSPECT_SECOND_COUNT";
    private static final String ATTR_INSPECT_PIECES_DELTA  = "INSPECT_PIECES_DELTA"; // ±n
    private static final String ATTR_INSPECT_JOB_ID        = "INSPECT_JOB_ID";
    private static final String ATTR_INSPECT_ROLE          = "INSPECT_ROLE";
    private static final String ROLE_MOVING                = "MOVING";
    private static final String ROLE_LEFT                  = "LEFT";

    // times 相關：跨重啟判斷用（寫入 attr，避免重開遺失）
    private static final String ATTR_INSPECT_FIRST_TIMES       = "INSPECT_FIRST_TIMES";        // 第一次完成當下 times
    private static final String ATTR_INSPECT_SECOND_BASE_TIMES = "INSPECT_SECOND_BASE_TIMES";  // 觸發第二次前 baseline times（稽核用、補救精準）
    private static final String ATTR_INSPECT_SECOND_TIMES      = "INSPECT_SECOND_TIMES";       // 第二次完成當下 times

    /**
     * 推斷型補救標記：0001111
     * - 當 secondCount 尚未可靠回讀（例如為 0），但我們用 times 推斷第二次已發生，為避免 job 卡死而先關單。
     * - 將此 flag 寫入 attr，方便日後稽核：這次 secondCount/delta 可能不是可靠數值。
     */
    private static final String ATTR_INSPECT_SECOND_INFERRED   = "INSPECT_SECOND_INFERRED";    // "1" 表示推斷補關

    /**
     * 定時輪詢相機狀態。
     * fixedDelay：前一次執行完成後，間隔 periodMs 再執行下一次（避免堆疊）。
     */
    @Scheduled(fixedDelayString = "${camera.modbus.poll.periodMs:250}")
    public void poll() {
        // 由設定總開關控制是否啟用輪詢
        if (!props.getPoll().isEnabled()) return;

        try {
            // 一次讀回兩台相機（減少 Modbus round-trip）
            TwoCamerasSnapshot s = svc.readSnapshot();

            // ---- Cam1（對應 gripper#4）----
            handleOneCamera(
                    "Cam1",
                    GRIPPER_ID_CAM1,
                    s.cam1().state(),
                    lastC1,
                    s.cam1().firstCount(),
                    s.cam1().secondCount(),
                    s.cam1().total(),
                    s.cam1().times(),
                    s.cam1().error(),
                    svc::triggerCam1First,
                    svc::triggerCam1Second,
                    secondJobCam1, baseSecondCntCam1, baseTotalCntCam1, baseTimesCam1
            );

            // ---- Cam2（對應 gripper#5）----
            handleOneCamera(
                    "Cam2",
                    GRIPPER_ID_CAM2,
                    s.cam2().state(),
                    lastC2,
                    s.cam2().firstCount(),
                    s.cam2().secondCount(),
                    s.cam2().total(),
                    s.cam2().times(),
                    s.cam2().error(),
                    svc::triggerCam2First,
                    svc::triggerCam2Second,
                    secondJobCam2, baseSecondCntCam2, baseTotalCntCam2, baseTimesCam2
            );

        } catch (Exception e) {
            // 這裡用 warn 避免排程因例外中斷；真正的 root cause 可在 svc 內打更詳細 log
            log.warn("[CameraModbus] poll failed: {}", e.getMessage());
        }
    }

    // ====================== 單台相機流程 ======================

    /**
     * 單台相機狀態處理：
     * - 讀 state / counters
     * - 查 job
     * - 在對的時機 trigger first/second
     * - 依 state 推進 job
     * - 處理第二次快閃與重啟補救
     */
    private void handleOneCamera(
            String camName,
            long gripperId,
            CameraState stateNow,
            AtomicReference<CameraState> prevRef,
            int firstCount, int secondCount, int total, int times, Object errCode,
            Runnable triggerFirst,
            Runnable triggerSecond,
            AtomicLong secondJobHolder,
            AtomicInteger baseSecondCntHolder,
            AtomicInteger baseTotalCntHolder,
            AtomicInteger baseTimesHolder
    ) {
        // 1) 狀態變更 log（只有 state 有變才印，避免洗版）
        CameraState prev = prevRef.getAndSet(stateNow);
        if (stateNow != prev) {
            log.info("[{}] {} first={} second={} total={} times={} err={}",
                    camName, stateNow, firstCount, secondCount, total, times, errCode);
        }

        // 2) 查 active job：每支 gripper 同時間只允許一筆 is_closed=0（由 DB uk_active_per_gripper 保證）
        Optional<InspectionJob> jobOpt = inspectionJobRepository.findActiveByGripper(gripperId);
        if (jobOpt.isEmpty()) {
            // 無 active job：若 idle 且 baseline 存在且計數成長 → 以 jobId 補關與補寫第二次數量 + 差額
            maybeAutoCloseOnIdleWithoutJob(
                    camName, stateNow, firstCount, secondCount, total, times,
                    secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder
            );
            return;
        }
        InspectionJob job = jobOpt.get();

        // 3) 相機錯誤 → 失敗關單（避免 job 卡死）
        if (stateNow == CameraState.ERROR) {
            updateJobStatus(job, J_FAILED, true);
            clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
            log.warn("[{}] Camera ERROR → job#{} 標記 FAILED 並關單", camName, job.getId());
            return;
        }

        // 4) 重開容錯（只在 stateNow==IDLE 時嘗試）：
        // - 避免在 SECOND_IN_PROGRESS 等狀態誤判補關。
        if (stateNow == CameraState.IDLE && shouldTryRecoverSecondDone(job)) {
            RecoverSecondDecision d = tryRecoverSecondDoneFromTimes(camName, job, firstCount, secondCount, times);
            if (d.recovered()) {
                // 已補關 DONE，避免後續又嘗試 trigger second
                clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
                return;
            }
        }

        // 5) 嘗試觸發 FIRST
        // - 必須 state=IDLE
        // - job status 必須在等待第一次的區間
        // - 夾爪要有帳
        // - 夾爪要到 firstStation
        if (stateNow == CameraState.IDLE && in(job.getStatus(), J_WAIT_MOVE_FIRST, J_MOVING_FIRST)) {
            if (gripperHoldingSomething(gripperId) && isGripperAtStationLevel(gripperId, job.getFirstStationId())) {
                log.info("[{}] Trigger FIRST (job#{}, g#{})", camName, job.getId(), gripperId);
                safeRun(camName, "FIRST", triggerFirst);
                return;
            }
        }

        // 6) FIRST 完成 → 寫 firstCount（優先寫到 moving/left 成對）+ 寫 FIRST_TIMES（跨重啟錨點）
        if (stateNow == CameraState.FIRST_DONE_WAIT_SECOND && jobStateBefore(job, J_FIRST_DONE)) {
            persistFirstCountsByJob(job.getId(), job.getContainerMainId(), firstCount);
            persistFirstTimesByJob(job.getId(), job.getContainerMainId(), times);
            updateJobStatus(job, J_FIRST_DONE, false);
            log.info("[{}] FIRST_DONE → job#{} 標記 FIRST_DONE（firstCount={} 已寫入 attr，firstTimes={} 已保存）",
                    camName, job.getId(), firstCount, times);
        }

        // 7) 嘗試觸發 SECOND
        // - 必須 state=FIRST_DONE_WAIT_SECOND（相機端允許第二次觸發的狀態）
        // - job status 必須在等待第二次的區間（或剛 FIRST_DONE）
        // - 夾爪要有帳且到 secondStation
        if (stateNow == CameraState.FIRST_DONE_WAIT_SECOND
                && in(job.getStatus(), J_WAIT_MOVE_SECOND, J_MOVING_SECOND, J_FIRST_DONE)) {

            // 觸發前短路：如果看起來第二次已完成（避免重複 trigger）
            if (alreadyLooksSecondDone(job, secondCount, times)) {
                RecoverSecondDecision d = recoverSecondAsDone(
                        camName, job, firstCount, secondCount, times,
                        "觸發 SECOND 前偵測已完成（避免重複觸發）"
                );
                if (d.recovered()) {
                    clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
                    return;
                }
            }

            if (gripperHoldingSomething(gripperId) && isGripperAtStationLevel(gripperId, job.getSecondStationId())) {
                log.info("[{}] Trigger SECOND (job#{}, g#{})", camName, job.getId(), gripperId);

                // 記憶體 baseline（快閃補關用）
                secondJobHolder.set(job.getId());
                baseSecondCntHolder.set(safeNonNeg(secondCount));
                baseTotalCntHolder.set(safeNonNeg(total));
                baseTimesHolder.set(safeNonNeg(times));

                // 同步保存 baseline 到 attr（重開容錯/稽核用；也是補救判斷更精準的 anchor）
                persistSecondBaseTimesByJob(job.getId(), job.getContainerMainId(), times);

                safeRun(camName, "SECOND", triggerSecond);
                return;
            }
        }

        // 8a) 正常 SECOND_DONE → 寫 secondCount + 差額 → DONE
        if (stateNow == CameraState.SECOND_DONE_AUTO_TO_IDLE && jobStateBefore(job, J_SECOND_DONE)) {
            persistSecondCountAndDeltaByJob(job.getId(), job.getContainerMainId(), firstCount, secondCount);
            persistSecondTimesByJob(job.getId(), job.getContainerMainId(), times);
            updateJobStatus(job, J_SECOND_DONE, false);
            updateJobStatus(job, J_DONE, true);
            clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
            log.info("[{}] SECOND_DONE → job#{} 標記 DONE（secondCount={} 與 delta 已寫入 attr，secondTimes={} 已保存）",
                    camName, job.getId(), secondCount, times);
            return;
        }

        // 8b) 快閃：直接回 IDLE 但計數成長 → 視為 SECOND_DONE，寫 secondCount + 差額 後補關
        if (stateNow == CameraState.IDLE) {
            Long awaitedJobId = secondJobHolder.get();
            if (awaitedJobId != 0L && awaitedJobId.equals(job.getId())) {
                boolean progressed =
                        secondCount > baseSecondCntHolder.get()
                                || total > baseTotalCntHolder.get()
                                || times > baseTimesHolder.get();
                if (progressed) {
                    persistSecondCountAndDeltaByJob(job.getId(), job.getContainerMainId(), firstCount, secondCount);
                    persistSecondTimesByJob(job.getId(), job.getContainerMainId(), times);
                    log.info("[{}] SECOND 快閃回 IDLE（偵測計數增加）→ job#{} 視為 SECOND_DONE", camName, job.getId());
                    updateJobStatus(job, J_SECOND_DONE, false);
                    updateJobStatus(job, J_DONE, true);
                    clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
                }
            }
        }
    }

    /**
     * 無 active job：若 idle 且基準存在又觀察到計數成長，嘗試以 jobId 原子補關，
     * 並補寫第二次數量 + 差額到該 job 所綁 moving/left。
     * <p>
     * 用途：
     * - 防止「job 可能被其他流程關掉/切換」後，這邊仍可在 IDLE 時補收尾（以 secondJobHolder 保存的 jobId 為準）。
     * <p>
     * 限制：
     * - 這段仍依賴記憶體 baseline；重開後 baseline 遺失，主要由 tryRecoverSecondDoneFromTimes 負責補救。
     */
    private void maybeAutoCloseOnIdleWithoutJob(
            String camName,
            CameraState stateNow,
            int firstCount, int secondCount, int total, int times,
            AtomicLong secondJobHolder,
            AtomicInteger baseSecondCntHolder,
            AtomicInteger baseTotalCntHolder,
            AtomicInteger baseTimesHolder
    ) {
        if (stateNow != CameraState.IDLE) return;

        Long awaitedJobId = secondJobHolder.get();
        if (awaitedJobId == 0L) return;

        boolean progressed =
                secondCount > baseSecondCntHolder.get()
                        || total > baseTotalCntHolder.get()
                        || times > baseTimesHolder.get();

        if (progressed) {
            try {
                // 先補寫 secondCount + 差額（若能查回 job 取得 cmId 以供 fallback）
                inspectionJobRepository.findById(awaitedJobId).ifPresent(j -> {
                    persistSecondCountAndDeltaByJob(awaitedJobId, j.getContainerMainId(), firstCount, secondCount);
                    persistSecondTimesByJob(awaitedJobId, j.getContainerMainId(), times);
                });

                // 目前實作呼叫 markSecondDoneAndCloseIfActive(awaitedJobId)，只關閉仍為 active 的檢測任務。
                inspectionJobRepository.markSecondDoneAndCloseIfActive(awaitedJobId);

                log.info("[{}] 無 active job，但以計數補關 job#{} 成功（secondCount 與 delta 已寫入 attr）",
                        camName, awaitedJobId);
            } catch (Exception e) {
                log.warn("[{}] 嘗試以計數補關 job#{} 失敗: {}", camName, awaitedJobId, e.getMessage());
            } finally {
                clearSecondBaseline(secondJobHolder, baseSecondCntHolder, baseTotalCntHolder, baseTimesHolder);
            }
        }
    }

    // ========================================================================
    // 重開容錯：利用 times 推斷第二次是否已完成
    // ========================================================================

    /**
     * 判斷該 job 是否處於「可能需要補救第二次」的狀態。
     * - 只針對仍在進行中（is_closed=0）且 status 在等待第二次的區間。
     */
    private boolean shouldTryRecoverSecondDone(InspectionJob job) {
        if (job == null) return false;

        // is_closed=1 代表已結束（成功或失敗），不做任何補救
        if (Boolean.TRUE.equals(job.getIsClosed())) return false;

        // status 在等待第二次的三個狀態才需要補救
        return in(job.getStatus(), J_WAIT_MOVE_SECOND, J_MOVING_SECOND, J_FIRST_DONE);
    }

    /**
     * 透過 attr 保存的 anchor 與目前回讀 times/secondCount 推斷：
     * - 若 secondCount > 0：幾乎可視為第二次已完成（前提：設備設計為 secondCount 只有第二次完成才會變 >0）
     * - secondCount=0 時：改用 times 與 anchor 比對（優先 SECOND_BASE_TIMES，其次 FIRST_TIMES）
     */
    private RecoverSecondDecision tryRecoverSecondDoneFromTimes(
            String camName,
            InspectionJob job,
            int firstCount,
            int secondCount,
            int timesNow
    ) {
        // secondCount 已經 > 0：直接補關（最可靠）
        if (secondCount > 0) {
            return recoverSecondAsDone(camName, job, firstCount, secondCount, timesNow,
                    "重開/快閃容錯：secondCount>0 推斷 SECOND 已完成");
        }

        // secondCount=0：使用 times 判斷
        Optional<Integer> anchorOpt = readRecoverAnchorTimes(job.getId());
        if (anchorOpt.isEmpty()) {
            // 沒有任何 anchor：保守不補（避免誤關）
            return RecoverSecondDecision.noop("no recover anchor (baseTimes/firstTimes)");
        }

        int anchor = anchorOpt.get();
        if (timesNow <= anchor) {
            // times 沒增加：代表 anchor 之後還沒發生第二次拍照事件
            return RecoverSecondDecision.noop("times not progressed (timesNow<=anchor)");
        }

        // times 增加：推斷第二次已拍（或至少拍照事件發生），補關
        return recoverSecondAsDone(camName, job, firstCount, secondCount, timesNow,
                "重開/快閃容錯：由 times(anchor) 推斷 SECOND 已完成");
    }

    /**
     * 觸發第二次前的短路判斷：如果 secondCount 已經 >0 或 times 已經比 anchor 大，代表第二次可能早已拍完。
     * <p>
     * anchor 選擇：
     * - 優先 SECOND_BASE_TIMES（最貼近「準備觸發第二次那一刻」）
     * - 次選 FIRST_TIMES
     */
    private boolean alreadyLooksSecondDone(InspectionJob job, int secondCount, int timesNow) {
        if (secondCount > 0) return true;

        Optional<Integer> anchorOpt = readRecoverAnchorTimes(job.getId());
        return anchorOpt.filter(anchor -> timesNow > anchor).isPresent();
    }

    /**
     * 讀取補救判斷用的 anchor times：
     * - 優先讀 SECOND_BASE_TIMES（在觸發第二次前會寫入）
     * - 若沒有，再讀 FIRST_TIMES（第一次完成會寫入）
     */
    private Optional<Integer> readRecoverAnchorTimes(Long jobId) {
        Optional<Integer> baseOpt = readIntAttrByJob(jobId, ATTR_INSPECT_SECOND_BASE_TIMES);
        if (baseOpt.isPresent()) return baseOpt;
        return readIntAttrByJob(jobId, ATTR_INSPECT_FIRST_TIMES);
    }

    /**
     * 統一收斂「視為第二次已完成」的處理：
     *   1) 寫 secondCount + delta（方案B：moving/left 成對）
     *   2) 寫 INSPECT_SECOND_TIMES（稽核/追蹤用）
     *   3) 若 secondCount==0（僅靠 times 推斷），額外寫 INSPECT_SECOND_INFERRED=1（稽核提示）
     *   4) job 狀態推進到 DONE（SECOND_DONE → DONE）
     */
    private RecoverSecondDecision recoverSecondAsDone(
            String camName,
            InspectionJob job,
            int firstCount,
            int secondCount,
            int timesNow,
            String reason
    ) {
        try {
            int sc = Math.max(0, secondCount);

            // 寫 secondCount + delta（若 sc=0，delta 會是 0；並以 inferred flag 標記這是推斷）
            persistSecondCountAndDeltaByJob(job.getId(), job.getContainerMainId(), firstCount, sc);
            persistSecondTimesByJob(job.getId(), job.getContainerMainId(), timesNow);

            if (sc == 0) {
                // secondCount 尚未可靠回讀，但為避免 job 卡死，採推斷補關 → 做稽核標記
                persistSecondInferredByJob(job.getId(), job.getContainerMainId(), true);
            }

            // 狀態推進：先 SECOND_DONE 再 DONE（也可以直接 DONE，但分段更利於稽核）
            updateJobStatus(job, J_SECOND_DONE, false);
            updateJobStatus(job, J_DONE, true);

            log.warn("[{}] {} → job#{} 補關 DONE（firstCount={} secondCount={} timesNow={} inferred={}）",
                    camName, reason, job.getId(), firstCount, secondCount, timesNow, (sc == 0));

            return RecoverSecondDecision.recovered(reason);
        } catch (Exception e) {
            log.warn("[{}] {} 補關失敗 job#{}: {}", camName, reason, job.getId(), e.getMessage());
            return RecoverSecondDecision.noop("recover failed");
        }
    }

    /**
     * 補救決策回傳物件：
     * - recovered=true 表示已補關/已收斂；呼叫端應停止後續 trigger / 推進
     * - note 用於 log / debug
     */
    private record RecoverSecondDecision(boolean recovered, String note) {
        static RecoverSecondDecision recovered(String note) { return new RecoverSecondDecision(true, note); }
        static RecoverSecondDecision noop(String note) { return new RecoverSecondDecision(false, note); }
    }

    // ========================================================================
    // ====== 依 Job 找 moving/left 並寫入（方案B） ============================
    // ========================================================================

    /** FIRST_DONE：優先對 moving/left 寫 firstCount；找不到成對時，回退寫 job.containerMainId。 */
    private void persistFirstCountsByJob(Long jobId, Long fallbackContainerId, int firstCount) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        if (pair.isPresent()) {
            long movingId = pair.get().movingId;
            long leftId   = pair.get().leftId;
            persistFirstCount(movingId, firstCount);
            persistFirstCount(leftId,   firstCount);
        } else if (fallbackContainerId != null) {
            persistFirstCount(fallbackContainerId, firstCount);
        }
    }

    /** FIRST_DONE：保存第一次完成時 times（跨重啟錨點）。 */
    private void persistFirstTimesByJob(Long jobId, Long fallbackContainerId, int times) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        String v = String.valueOf(safeNonNeg(times));
        if (pair.isPresent()) {
            upsertAttr(pair.get().movingId, ATTR_INSPECT_FIRST_TIMES, v);
            upsertAttr(pair.get().leftId,   ATTR_INSPECT_FIRST_TIMES, v);
        } else if (fallbackContainerId != null) {
            upsertAttr(fallbackContainerId, ATTR_INSPECT_FIRST_TIMES, v);
        }
    }

    /** 觸發 SECOND 前：保存 baseline times（跨重啟用／稽核用）。 */
    private void persistSecondBaseTimesByJob(Long jobId, Long fallbackContainerId, int times) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        String v = String.valueOf(safeNonNeg(times));
        if (pair.isPresent()) {
            upsertAttr(pair.get().movingId, ATTR_INSPECT_SECOND_BASE_TIMES, v);
            upsertAttr(pair.get().leftId,   ATTR_INSPECT_SECOND_BASE_TIMES, v);
        } else if (fallbackContainerId != null) {
            upsertAttr(fallbackContainerId, ATTR_INSPECT_SECOND_BASE_TIMES, v);
        }
    }

    /** SECOND_DONE：保存第二次完成時 times（稽核/追蹤）。 */
    private void persistSecondTimesByJob(Long jobId, Long fallbackContainerId, int times) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        String v = String.valueOf(safeNonNeg(times));
        if (pair.isPresent()) {
            upsertAttr(pair.get().movingId, ATTR_INSPECT_SECOND_TIMES, v);
            upsertAttr(pair.get().leftId,   ATTR_INSPECT_SECOND_TIMES, v);
        } else if (fallbackContainerId != null) {
            upsertAttr(fallbackContainerId, ATTR_INSPECT_SECOND_TIMES, v);
        }
    }

    /**
     * SECOND_DONE：推斷補關標記（稽核用）。
     * - inferred=true → 寫 "1"
     * - inferred=false → 可寫 "0" 或刪除（這裡採寫 "0" 以保留歷史）
     */
    private void persistSecondInferredByJob(Long jobId, Long fallbackContainerId, boolean inferred) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        String v = inferred ? "1" : "0";
        if (pair.isPresent()) {
            upsertAttr(pair.get().movingId, ATTR_INSPECT_SECOND_INFERRED, v);
            upsertAttr(pair.get().leftId,   ATTR_INSPECT_SECOND_INFERRED, v);
        } else if (fallbackContainerId != null) {
            upsertAttr(fallbackContainerId, ATTR_INSPECT_SECOND_INFERRED, v);
        }
    }

    /** SECOND_DONE：對 moving/left 寫 secondCount 與 ±delta；找不到成對時，回退寫 job.containerMainId（不寫 delta）。 */
    private void persistSecondCountAndDeltaByJob(Long jobId, Long fallbackContainerId, int firstCount, int secondCount) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        if (pair.isPresent()) {
            long movingId = pair.get().movingId;
            long leftId   = pair.get().leftId;

            persistSecondCount(movingId, secondCount);
            persistSecondCount(leftId,   secondCount);

            // delta：第二次 - 第一次（不足視為 0）
            int delta = Math.max(0, secondCount - firstCount);

            // moving 容器記正數、left 容器記負數：符合先前的帳務語意（+n / -n）
            upsertAttr(movingId, ATTR_INSPECT_PIECES_DELTA, "+" + delta);
            upsertAttr(leftId,   ATTR_INSPECT_PIECES_DELTA, "-" + delta);

            log.info("[InspectDelta] job#{} moving#{}+= {} , left#{}-= {}", jobId, movingId, delta, leftId, delta);
        } else if (fallbackContainerId != null) {
            // 找不到成對：仍至少寫 secondCount（避免完全漏資料）
            persistSecondCount(fallbackContainerId, secondCount);
            log.warn("[InspectDelta] job#{} 找不到 moving/left 綁定，僅寫 secondCount 至 cm#{}", jobId, fallbackContainerId);
        }
    }

    /**
     * 由 attr(INSPECT_JOB_ID) 找到該 job 的 moving/left 成對容器。
     * <p>
     * 依賴：
     * - container_attr 內需存在：
     *   - (cmId, INSPECT_JOB_ID, jobId)
     *   - (cmId, INSPECT_ROLE, MOVING/LEFT)
     * <p>
     * Repository 需求：
     * - findByKeyAndValue(key, value)：回傳所有 attr rows（可能多筆 cmId）
     * - findOne(cmId, key)：取單一 key 的值
     */
    private Optional<PairIds> resolvePairByJobId(Long jobId) {
        if (jobId == null) return Optional.empty();
        String jobVal = String.valueOf(jobId);

        try {
            List<ContainerAttr> binds = containerAttrRepository.findByKeyAndValue(ATTR_INSPECT_JOB_ID, jobVal);
            if (binds == null || binds.isEmpty()) return Optional.empty();

            Long movingId = null, leftId = null;

            for (ContainerAttr a : binds) {
                Long cmId = a.getContainerMainId();
                if (cmId == null) continue;

                // 取該容器的角色（MOVING / LEFT）
                String role = containerAttrRepository.findOne(cmId, ATTR_INSPECT_ROLE)
                        .map(ContainerAttr::getAttrValue)
                        .orElse(null);

                if (ROLE_MOVING.equals(role)) {
                    movingId = cmId;
                } else if (ROLE_LEFT.equals(role)) {
                    leftId = cmId;
                }
            }

            if (movingId != null && leftId != null) {
                return Optional.of(new PairIds(movingId, leftId));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[InspectBind] 解析 job#{} 的 moving/left 失敗: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }

    /** 成對容器 id：moving / left */
    private record PairIds(long movingId, long leftId) {}

    // ========================================================================
    // ====== Attr 寫入 ========================================================
    // ========================================================================

    /** 寫入第一次完成數量（firstCount） */
    private void persistFirstCount(Long containerMainId, int firstCount) {
        if (containerMainId == null) return;
        upsertAttr(containerMainId, ATTR_INSPECT_FIRST_COUNT, String.valueOf(safeNonNeg(firstCount)));
    }

    /** 寫入第二次完成數量（secondCount） */
    private void persistSecondCount(Long containerMainId, int secondCount) {
        if (containerMainId == null) return;
        upsertAttr(containerMainId, ATTR_INSPECT_SECOND_COUNT, String.valueOf(safeNonNeg(secondCount)));
    }

    /**
     * Upsert attr：有就更新、沒有就新增。
     * 若 Repository 已有 upsert 方法（例如 atomic upsert），可直接替換為該方法以降低競態。
     */
    private void upsertAttr(Long containerMainId, String key, String value) {
        try {
            Optional<ContainerAttr> opt = containerAttrRepository.findOne(containerMainId, key);
            if (opt.isPresent()) {
                ContainerAttr a = opt.get();
                a.setAttrValue(value);
                a.setUpdatedTime(LocalDateTime.now());
                containerAttrRepository.update(a);
            } else {
                ContainerAttr a = new ContainerAttr();
                a.setContainerMainId(containerMainId);
                a.setAttrKey(key);
                a.setAttrValue(value);
                a.setCreatedTime(LocalDateTime.now());
                a.setUpdatedTime(LocalDateTime.now());
                containerAttrRepository.save(a);
            }
        } catch (Exception e) {
            log.warn("[CameraModbus] upsertAttr 失敗 cm#{} key={} val={}: {}", containerMainId, key, value, e.getMessage());
        }
    }

    /**
     * 由 jobId 讀取「成對 moving/left」其中任一方的 int attr 值。
     * <p>
     * 假設：
     * - moving/left 會寫相同值（本類 persistFirstTimes/persistSecondTimes/persistSecondBaseTimes 都是成對一起寫）
     * <p>
     * 若未來允許 moving/left 不一致，這裡就需要改成：
     * - 讀 movingId 與 leftId 各自的值
     * - 做一致性檢查（不一致就 log warn，並採用較保守策略）
     */
    private Optional<Integer> readIntAttrByJob(Long jobId, String key) {
        Optional<PairIds> pair = resolvePairByJobId(jobId);
        try {
            if (pair.isPresent()) {
                long movingId = pair.get().movingId;
                return containerAttrRepository.findOne(movingId, key)
                        .map(ContainerAttr::getAttrValue)
                        .flatMap(this::tryParseInt);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[CameraModbus] readIntAttrByJob 失敗 job#{} key={}: {}", jobId, key, e.getMessage());
            return Optional.empty();
        }
    }

    /** 字串轉 int：轉換失敗回 Optional.empty() */
    private Optional<Integer> tryParseInt(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(Integer.parseInt(s.trim()));
        } catch (Exception ignore) {
            return Optional.empty();
        }
    }

    // ========================================================================
    // ====== 小工具／通用方法 =================================================
    // ========================================================================

    /** 清除內存 baseline（重開本來就會清；正常流程結束也應清） */
    private void clearSecondBaseline(AtomicLong jobHolder, AtomicInteger s, AtomicInteger t, AtomicInteger ti) {
        jobHolder.set(0L);
        s.set(0);
        t.set(0);
        ti.set(0);
    }

    /** 安全執行 trigger（避免任何例外打爆排程） */
    private void safeRun(String camName, String phase, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("[{}] Trigger {} 失敗: {}", camName, phase, e.getMessage());
        }
    }

    /** 防止負數（相機偶發回傳異常值時，至少不寫負數進 DB） */
    private int safeNonNeg(int v) { return Math.max(0, v); }

    /** job 狀態若已是 target，就不必重複寫（避免不必要 update） */
    private boolean jobStateBefore(InspectionJob job, String targetToWrite) {
        return !targetToWrite.equals(job.getStatus());
    }

    /**
     * 更新 job 狀態：
     * - close=true：同步 is_closed=1（代表此 job 工作流結束，成功或失敗）
     */
    private void updateJobStatus(InspectionJob job, String newStatus, boolean close) {
        try {
            job.setStatus(newStatus);
            if (close) {
                job.setIsClosed(true);
            }
            job.setUpdatedTime(LocalDateTime.now());
            inspectionJobRepository.update(job);
        } catch (Exception e) {
            log.warn("[CameraModbus] 無法更新 job#{} 狀態至 {}: {}", job.getId(), newStatus, e.getMessage());
        }
    }

    /** 夾爪上是否「有帳」（代表抓到 container 在 gripper 上） */
    private boolean gripperHoldingSomething(long gripperId) {
        try {
            return locationTrackingRepository.findContainerOnGripper(gripperId).isPresent();
        } catch (Exception e) {
            log.warn("[CameraModbus] 查詢夾爪是否有帳失敗 g#{}: {}", gripperId, e.getMessage());
            return false;
        }
    }

    /**
     * 判斷「夾爪是否位於指定 station」：
     * - stationId → station.location_point_id → location_point.name（如 "VIRTUAL#6"）
     * - 從 name 解析出 level（"#" 後、或名稱中的第一段數字）
     * - 與 PLC level 比對；名稱以 "VIRTUAL" 開頭者 +200（對應 PLC level 映射）
     */
    private boolean isGripperAtStationLevel(long gripperId, Long stationId) {
        if (stationId == null) return false;

        try {
            Optional<String> nameOpt = inspectionStationRepository.findById(stationId)
                    .flatMap(st -> locationPointRepository.findById(st.getLocationPointId()))
                    .map(LocationPoint::getName);
            if (nameOpt.isEmpty()) return false;

            Optional<Integer> targetLevelOpt = parseLevelFromLocationName(nameOpt.get());
            if (targetLevelOpt.isEmpty()) return false;

            String gn = "Gripper#" + gripperId;
            GripperDeviceStatus ds = gripperStatusCache.getLatest(gn);

            // isValidAndComplete(3)：原本的 freshness 機制（資料 3 秒內視為新鮮）
            boolean fresh = ds != null && ds.isValidAndComplete(3);
            if (!fresh) return false;

            Integer curr = safeGetLevel(ds);
            return curr != null && curr.equals(targetLevelOpt.get());
        } catch (Exception e) {
            log.warn("[CameraModbus] 判位失敗 stationId={} g#{}: {}", stationId, gripperId, e.getMessage());
            return false;
        }
    }

    /**
     * 從 location name 解析 level：
     * - 先找最後一個 '#'
     * - 若 # 後是數字，取該數字
     * - 否則取字串中第一段連續數字
     * - name 以 "VIRTUAL" 開頭則 +200（對應 PLC level 映射）
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
                    int level = Integer.parseInt(tail.substring(0, end));
                    if (name.startsWith("VIRTUAL")) {
                        level += 200;
                    }
                    return Optional.of(level);
                } catch (NumberFormatException ignore) {
                    // 解析失敗時，改走抽取第一段數字的解析流程。
                }
            }
        }

        // fallback：抓第一段連續數字
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        if (sb.length() > 0) {
            try {
                int level = Integer.parseInt(sb.toString());
                if (name.startsWith("VIRTUAL")) {
                    level += 200;
                }
                return Optional.of(level);
            } catch (NumberFormatException ignore) {
                // noop
            }
        }
        return Optional.empty();
    }

    /** 安全取 ds.getLevel()（避免 ds 欄位 null 或其他 runtime 例外） */
    private Integer safeGetLevel(GripperDeviceStatus ds) {
        try { return ds.getLevel(); }
        catch (Throwable ignore) { return null; }
    }

    /** 工具：判斷 v 是否在 options 中 */
    private static boolean in(String v, String... options) {
        if (v == null) return false;
        for (String o : options) if (v.equals(o)) return true;
        return false;
    }
}
