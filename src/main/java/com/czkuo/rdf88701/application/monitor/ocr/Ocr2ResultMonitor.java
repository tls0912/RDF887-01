package com.czkuo.rdf88701.application.monitor.ocr;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.ocr.OcrCommandService;
import com.czkuo.rdf88701.application.service.ocr.OcrTaskCoordinator;
import com.czkuo.rdf88701.common.dto.OcrCommandResult;
import com.czkuo.rdf88701.domain.dto.ocr.OcrTaskStatusResponse;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.czkuo.rdf88701.application.monitor.ocr.Ocr2Io.*;

/**
 * Ocr2ResultMonitor
 * -----------------------------------------------------------------------------
 * 職責：
 *   - 在 OCR#2 到位且條件允許時：
 *       1) 建立 / 接手 OCR 任務
 *       2) 等待 OCR 結果（DB / Vendor）
 *       3) 回報 PLC（Collect）
 *
 * 核心設計原則：
 *   - ❌ 不檢查 Standby
 *   - ✅ 僅在以下條件成立時才會動作：
 *       - Bay == DOWN
 *       - Level == Site#12 / Site#14
 *       - s == 1 (IDLE)
 *       - MOVE 交握位全關（CMD / COMP）
 *
 *   - OCR 派單前一定要確認：
 *       👉 OCR Device 當下「acceptingTask == true」
 *
 *   - 避免行為：
 *       ❌ Vendor 忙碌時反覆 createTask 被 reject
 *       ❌ log 被洗爆
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ocr2ResultMonitor {

    private final PlcAccessService plc;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;

    private final OcrTaskCoordinator ocrCoordinator;
    private final OcrCommandService  ocrService;
    private final OcrTaskRepository ocrTaskRepository;

    // -------------------------------------------------------------------------
    // Session 狀態（單一 container 維度）
    // -------------------------------------------------------------------------
    private Long    currentContainerId = null;

    private boolean taskDispatched     = false;
    private Long    ocrTaskId          = null;
    private long    dispatchedAt       = 0L;

    private boolean collectInProgress  = false;
    private long    collectReqSentAt   = 0L;

    private int     ocrAttempts        = 0;

    // -------------------------------------------------------------------------
    // Vendor polling / Device status 節流
    // -------------------------------------------------------------------------
    private long    lastPollAtMs           = 0L;

    private long    lastDevicePollAtMs     = 0L;
    private Boolean lastDeviceAccepting    = null;

    @Scheduled(fixedDelay = 600)
    public void monitor() {
        try {
            // 0) 若正在 Collect 握手，優先收斂（不做任何新決策）
            if (handleCollectClosing()) return;

            // 1) 僅服務 Site#12 / Site#14
            int level = plc.readInt16(DEVICE, W_POS_LEVEL);
            if (!(level == SITE12_LEVEL || level == SITE14_LEVEL)) { resetAll(); return; }

            // 2) 位置是否下降位
            if (!isAtDown()) { resetAll(); return; }

            // 3) 狀態與交握是否「乾淨」
            if (!(isStatusIdle() && isHandshakeClear())) { return; }

            // 4) 該站是否有容器 & 是否需要 OCR
            String siteName = level == SITE12_LEVEL ? SITE12_NAME : SITE14_NAME;
            Optional<Long> cmOpt = locationTrackingRepository.findContainerAtLocationName(siteName);
            if (cmOpt.isEmpty()) { resetAll(); return; }
            Long cmId = cmOpt.get();

            // 5) 是否需要 OCR
            boolean needOcr = needsOcr(cmId);
            if (!needOcr) {
                // 已有結果 → 若曾派單，直接回報成功；否則什麼都不做
                if (taskDispatched && !collectInProgress) {
                    sendCollectAndBegin(true);
                } else {
                    resetOcrSession();
                }
                return;
            }

            // 6) Container 切換 → 清 session
            if (!cmId.equals(currentContainerId)) {
                currentContainerId = cmId;
                resetOcrSession();
                log.info("[OCR2-Result] 新容器 cm#{} @{}", cmId, siteName);
            }

            // 7) 尚未派單 → 先接手舊任務，再決定是否派新任務
            if (!taskDispatched) {

                // 7.1 接手 DB 中未完成的 OCR 任務
                if (adoptExistingOcrTask(cmId)) {
                    taskDispatched = true;
                    dispatchedAt   = System.currentTimeMillis();
                    log.info("[OCR2-Result] ♻️ Adopt existing OCR task: cm#{} taskId={}", cmId, ocrTaskId);
                }
                // 7.2 派新任務（前置：Device 必須 acceptingTask）
                else {
                    if (!deviceAcceptingNow()) {
                        // Device 忙碌 / 不可接單 → 本輪安靜退出
                        return;
                    }

                    if (dispatchOcr(cmId)) {
                        taskDispatched = true;
                        dispatchedAt   = System.currentTimeMillis();
                    }
                    return;
                }
            }

            // 8) 已派單 → 嘗試決定 OCR 結果
            if (!collectInProgress) {
                boolean decided = false;

                // 8.1 以本地 ocr_task 狀態為優先
                if (ocrTaskId != null) {
                    PollOutcome byTask = pollDbTaskOnce(cmId, ocrTaskId);
                    if (byTask == PollOutcome.SUCCESS) { sendCollectAndBegin(true);  decided = true; }
                    else if (byTask == PollOutcome.FAIL) { sendCollectAndBegin(false); decided = true; }
                }

                // 8.2 DB（人工 / 他方寫入）
                if (!decided) {
                    ReleaseDecision byDb = decideCollectByDb(cmId);
                    if (byDb == ReleaseDecision.SUCCESS) {
                        sendCollectAndBegin(true); decided = true;
                    } else if (byDb == ReleaseDecision.FAIL) {
                        sendCollectAndBegin(false); decided = true;
                    }
                }

                // 8.3 Vendor 狀態（節流）
                if (!decided && ocrTaskId != null) {
                    PollOutcome byVendor = pollVendorOnce(cmId, ocrTaskId);
                    if (byVendor == PollOutcome.SUCCESS) { sendCollectAndBegin(true); decided = true; }
                    else if (byVendor == PollOutcome.FAIL) { sendCollectAndBegin(false); decided = true; }
                }

                // 8.4 TTL 超時 → FAIL
                // if (!decided && System.currentTimeMillis() - dispatchedAt > OCR_RESULT_TTL_MS) {
                //     log.warn("[OCR2-Result] ⏱️ OCR RESULT TTL → Collect(FAIL). cm#{}", cmId);
                //     writeOcrTextsIfAbsent(cmId, "OCR_TIMEOUT", null);
                //     sendCollectAndBegin(false);
                //     decided = true;
                // }

                if (decided) return; // 交給 handleCollectClosing() 收尾
            }

        } catch (Exception e) {
            log.error("[OCR2-Result] Monitor exception", e);
        }
    }

    // ---------- OCR 任務觸發 / 輪詢 ----------

    /**
     * OCR Device 是否可接單（acceptingTask）
     *
     * 設計：
     *   - 有節流（避免每 1.2s 打 vendor）
     *   - vendor 未回 acceptingTask → 保守視為不可接單
     *   - 發生例外 → 保守視為不可接單
     */
    private boolean deviceAcceptingNow() {
        long now = System.currentTimeMillis();

        // 節流：沿用上一筆結果
        if (now - lastDevicePollAtMs < DEVICE_STATUS_POLL_MIN_INTERVAL_MS) {
            return Boolean.TRUE.equals(lastDeviceAccepting);
        }
        lastDevicePollAtMs = now;

        try {
            return ocrService.getDeviceStatus(OCR_DEVICE_ID)
                    .map(ds -> {
                        Boolean acc = ds.getAcceptingTask();
                        lastDeviceAccepting = acc;
                        if (!Boolean.TRUE.equals(acc)) {
                            //log.debug("[OCR2-Result] OCR device not acceptingTask. status={}, accepting={}",
//                                    ds.getStatus(), acc);
                        }
                        return Boolean.TRUE.equals(acc);
                    })
                    .orElseGet(() -> {
                        lastDeviceAccepting = false;
                        return false;
                    });
        } catch (Exception e) {
            lastDeviceAccepting = false;
            log.warn("[OCR2-Result] getDeviceStatus error: {}", e.toString());
            return false;
        }
    }

    private boolean dispatchOcr(Long cmId) {
        OcrCommandResult res = ocrCoordinator.createAndDispatch(OCR_DEVICE_ID, cmId);
        if (!res.isSuccess() || Boolean.FALSE.equals(res.getVendorAccepted())) {
            log.warn("[OCR2-Result] 📤 Dispatch NG: {} | accepted={}", res.getMessage(), res.getVendorAccepted());
            return false;
        }
        this.ocrTaskId = res.getTaskId();
        log.info("[OCR2-Result] 📤 Dispatch OK: cm#{} taskId={}", cmId, ocrTaskId);
        return true;
    }

    /** 嘗試接手 container 對應的「未完成」 OCR 任務（有就綁定 ocrTaskId）。 */
    private boolean adoptExistingOcrTask(Long cmId) {
        // 先查「是否存在未完成」
        if (!ocrTaskRepository.existsUnfinishedForContainer(cmId)) return false;

        // 找「最新一筆」當前容器的任務
        return ocrTaskRepository.findLatestByContainerId(cmId)
                .filter(t -> isOcrTaskPending(t.getStatus()))
                .map(t -> {
                    this.ocrTaskId = t.getId();
                    return true;
                })
                .orElse(false);
    }

    private enum PollOutcome { PENDING, SUCCESS, FAIL }

    /** 單次輪詢 Vendor（節流；成功可順便回寫 DB）。 */
    private PollOutcome pollVendorOnce(Long cmId, Long taskId) {
        long now = System.currentTimeMillis();
        if (now - lastPollAtMs < POLL_MIN_INTERVAL_MS) return PollOutcome.PENDING;
        lastPollAtMs = now;

        try {
            Optional<OcrTaskStatusResponse> opt = ocrService.getTaskStatus(taskId);
            if (opt.isEmpty()) return PollOutcome.PENDING;

            OcrTaskStatusResponse r = opt.get();
            String status = safeVendorStatus(r);
            if (isVendorPending(status)) return PollOutcome.PENDING;

            if (isVendorCompletedOk(status)) {
                String[] texts = extractTexts(r);
                if (texts[0] != null || texts[1] != null) writeOcrTexts(cmId, texts[0], texts[1]);
                return PollOutcome.SUCCESS;
            }
            // 非 pending 且非 success → 視為失敗（避免死等）
            ocrAttempts++;
            if (ocrAttempts >= MAX_OCR_RETRY) return PollOutcome.FAIL;
            return PollOutcome.PENDING;

        } catch (Exception e) {
            log.warn("[OCR2-Result] poll vendor error: {}", e.toString());
            ocrAttempts++;
            if (ocrAttempts >= MAX_OCR_RETRY) return PollOutcome.FAIL;
            return PollOutcome.PENDING;
        }
    }

    /** 依 ocr_task（本地 DB）狀態決策；若成功也會回寫 ContainerData。 */
    private PollOutcome pollDbTaskOnce(Long cmId, Long taskId) {
        try {
            Optional<OcrTask> opt = ocrTaskRepository.findById(taskId);
            if (opt.isEmpty()) return PollOutcome.PENDING;

            OcrTask t = opt.get();
            String st = safeTaskStatus(t.getStatus());

            if (isTaskPending(st)) return PollOutcome.PENDING;

            if (isTaskSucceeded(st)) {
                String t1 = blankToNull(t.getOcrText1());
                String t2 = blankToNull(t.getOcrText2());
                if (t1 != null || t2 != null) writeOcrTexts(cmId, t1, t2);
                return PollOutcome.SUCCESS;
            }

            if (isTaskFailed(st)) {
                writeOcrTextsIfAbsent(cmId, "OCR_FAIL", null);
                return PollOutcome.FAIL;
            }

            // 其他未知狀態：保持 pending
            return PollOutcome.PENDING;

        } catch (Exception e) {
            log.warn("[OCR2-Result] poll db error: {}", e.toString());
            return PollOutcome.PENDING;
        }
    }

    // ---------- Collect：回報 PLC 與握手收斂 ----------

    /**
     * 寫 retcode 並拉起 B_COLLECT_REQ，開始 Collect 握手。
     * 不檢查 Standby；但要求：
     *   - s==1 (IDLE) 且 MOVE 交握位全關 才送 Collect（避免與 MOVE 衝突）。
     */
    private void sendCollectAndBegin(boolean success) {
        if (!isStatusIdle() || !isHandshakeClear()) {
            // MOVE 或狀態尚未乾淨（例如剛 COMPLETE 還在收斂），等下一輪再送
            return;
        }
        plc.writeInt32(DEVICE, W_OCR_RETCODE, success ? 0x0100 : 0x0F00);
        plc.writeBoolean(DEVICE, B_COLLECT_REQ, true);
        collectInProgress = true;
        collectReqSentAt  = System.currentTimeMillis();
        log.info("[OCR2-Result] ▶️ Collect begin. ret={}", success ? "0x0100" : "0x0F00");
    }

    /**
     * 收斂 Collect：
     * - 對方 B_COLLECT_ACK=1 → 我方放掉 B_COLLECT_REQ=0
     * - 我方 B_COLLECT_REQ 已為 0 → 視為結束，清 Session
     * - 長時間未收到 ACK → 超時強制放掉
     */
    private boolean handleCollectClosing() {
        if (!collectInProgress) return false;

        if (plc.readBoolean(DEVICE, B_COLLECT_ACK)) {
            plc.writeBoolean(DEVICE, B_COLLECT_REQ, false);
            return true;
        }

        if (!plc.readBoolean(DEVICE, B_COLLECT_REQ)) {
            log.info("[OCR2-Result] ✅ Collect closed.");
            resetOcrSession();
            return true;
        }

        if (System.currentTimeMillis() - collectReqSentAt > COLLECT_ACK_TIMEOUT_MS) {
            log.warn("[OCR2-Result] ⏱️ Collect ACK timeout; force close.");
            plc.writeBoolean(DEVICE, B_COLLECT_REQ, false);
            resetOcrSession();
            return true;
        }
        return true; // 仍在握手中
    }

    // ---------- DB / 判斷 ----------

    /** 是否需要 OCR：兩欄皆空才需要。 */
    private boolean needsOcr(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId)
                .map(d -> isBlank(d.getOcrText1()) && isBlank(d.getOcrText2()))
                .orElse(true);
    }

    /** 依 DB 內容決定 Collect 類型。 */
    private enum ReleaseDecision { SUCCESS, FAIL, NONE }
    private ReleaseDecision decideCollectByDb(Long cmId) {
        return containerDataRepository.findByContainerMainId(cmId).map(d -> {
            String t1 = safeTrim(d.getOcrText1());
            String t2 = safeTrim(d.getOcrText2());
            if (t1.isEmpty() && t2.isEmpty()) return ReleaseDecision.NONE;
            String both = (t1 + "|" + t2).toUpperCase();
            if (both.contains("OCR_FAIL") || both.contains("OCR_TIMEOUT") || both.contains("FAIL")) {
                return ReleaseDecision.FAIL;
            }
            return ReleaseDecision.SUCCESS;
        }).orElse(ReleaseDecision.NONE);
    }

    /** 若 DB 還是空才寫，避免覆蓋人工已填的結果。 */
    private void writeOcrTextsIfAbsent(Long cmId, String t1, String t2) {
        containerDataRepository.findByContainerMainId(cmId).ifPresentOrElse(cd -> {
            boolean empty = isBlank(cd.getOcrText1()) && isBlank(cd.getOcrText2());
            if (empty) {
                cd.setOcrText1(t1);
                cd.setOcrText2(t2);
                containerDataRepository.update(cd);
            }
        }, () -> {
            ContainerData n = new ContainerData();
            n.setContainerMainId(cmId);
            n.setOcrText1(t1);
            n.setOcrText2(t2);
            containerDataRepository.save(n);
        });
    }
    private void writeOcrTexts(Long cmId, String t1, String t2) {
        containerDataRepository.findByContainerMainId(cmId).ifPresentOrElse(cd -> {
            cd.setOcrText1(t1);
            cd.setOcrText2(t2);
            containerDataRepository.update(cd);
        }, () -> {
            ContainerData n = new ContainerData();
            n.setContainerMainId(cmId);
            n.setOcrText1(t1);
            n.setOcrText2(t2);
            containerDataRepository.save(n);
        });
    }

    // ---------- 判斷工具（不檢查 Standby，要求 s==1） ----------

    /** MOVE 交握位是否全關：CMD_REQ=0、CMD_ACK=0、COMP_REQ=0、COMP_ACK=0。 */
    private boolean isHandshakeClear() {
        if (plc.readBoolean(DEVICE, B_CMD_REQ))  return false;
        if (plc.readBoolean(DEVICE, B_CMD_ACK))  return false;
        if (plc.readBoolean(DEVICE, B_COMP_REQ)) return false;
        if (plc.readBoolean(DEVICE, B_COMP_ACK)) return false;
        return true;
    }

    /** 是否在下降位（Bay==2）。 */
    private boolean isAtDown() { return plc.readInt16(DEVICE, W_POS_BAY) == BAY_DOWN; }

    /** 是否 s==1（IDLE）。 */
    private boolean isStatusIdle() {
        int s = plc.readInt16(DEVICE, W_STATUS) & 0xFF; // s: 1 Idle / 2 Processing / 3 Complete
        return s == 1;
    }

    // ---------- 狀態解讀小工具 ----------
    private String safeVendorStatus(OcrTaskStatusResponse r) { try { return r.getStatus(); } catch (Throwable ignore) { return null; } }
    private boolean isVendorPending(String s) {
        if (s == null) return true;
        String u = s.trim().toUpperCase();
        return u.contains("PENDING") || u.contains("QUEUE") || u.contains("RUNNING") || u.contains("PROCESS");
    }
    private boolean isVendorCompletedOk(String s) {
        if (s == null) return false;
        String u = s.trim().toUpperCase();
        return u.equals("SUCCESS") || u.equals("SUCCEEDED") || u.equals("COMPLETED");
    }

    private String safeTaskStatus(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private boolean isTaskPending(String s) {
        if (s == null) return true;
        String u = s.trim().toUpperCase();
        return u.isEmpty() || u.contains("PENDING") || u.contains("QUEUE") || u.contains("RUNNING") || u.contains("PROCESS");
    }
    private boolean isTaskSucceeded(String s) {
        if (s == null) return false;
        String u = s.trim().toUpperCase();
        return u.equals("SUCCESS") || u.equals("SUCCEEDED") || u.equals("COMPLETED");
    }
    private boolean isTaskFailed(String s) {
        if (s == null) return false;
        String u = s.trim().toUpperCase();
        return u.equals("FAILED") || u.equals("FAIL") || u.equals("CANCELLED") || u.equals("CANCELED") || u.equals("TIMEOUT");
    }
    private boolean isOcrTaskPending(String status) { return isTaskPending(status); }

    private String[] extractTexts(OcrTaskStatusResponse r) {
        String t1 = null, t2 = null;
        try { t1 = blankToNull(r.getOcrText1()); } catch (Throwable ignore) {}
        try { t2 = blankToNull(r.getOcrText2()); } catch (Throwable ignore) {}
        return new String[]{t1, t2};
    }
    private String blankToNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }

    // ---------- Session ----------
    private void resetOcrSession() {
        taskDispatched    = false;
        ocrTaskId         = null;
        dispatchedAt      = 0L;
        collectInProgress = false;
        collectReqSentAt  = 0L;
        ocrAttempts       = 0;
        lastPollAtMs      = 0;
    }
    private void resetAll() { resetOcrSession(); currentContainerId = null; }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String  safeTrim(String s) { return s == null ? "" : s.trim(); }
}
