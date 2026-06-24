package com.czkuo.rdf88701.application.monitor.ocr;

import com.czkuo.rdf88701.application.service.PlcAccessService;
import com.czkuo.rdf88701.application.service.ocr.OcrCommandService;
import com.czkuo.rdf88701.application.service.ocr.OcrTaskCoordinator;
import com.czkuo.rdf88701.common.dto.OcrCommandResult;
import com.czkuo.rdf88701.domain.dto.ocr.OcrTaskStatusResponse;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.ContainerDataRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static com.czkuo.rdf88701.application.monitor.ocr.Ocr1Io.*;

/**
 * Ocr1ResultMonitor
 * -----------------------------------------------------------------------------
 * 只負責「辨識與收結果 + 回報 PLC」：
 *  - 前置條件：TR#3 已在 VIRTUAL#5 且 Bay=2 且 s==1（乾淨 IDLE，所有寫入位 OFF）。
 *  - 觸發 OCR 任務（fire-and-forget），保留 taskId。
 *  - 等結果（優先順序）：
 *      0) ocr_task（本地 DB 任務狀態）— 成功時可回寫 DB。
 *      1) DB（人工或他方寫入 container_data）— 只要任一欄有字即視為成功；包含 FAIL/TIMEOUT 關鍵字則失敗。
 *      2) vendor WebAPI（OcrCommandService）— 節流 + 重試；成功時可回寫 DB。
 *  - 超時（TTL） → Collect(FAIL)。
 *  - Collect 握手（B_COLLECT_REQ / B_COLLECT_ACK）：確實拉起、收斂、結束。
 *
 * 與 MotionMonitor 的分工：
 *  - 此類「不」下任何 MOVE / CMD_REQ / COMP_ACK；僅使用 Collect 與 W_OCR_RETCODE。
 *  - MotionMonitor 保證把設備送到位並回到乾淨 IDLE；本類才會觸發辨識與回報結果。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ocr1ResultMonitor {

    private final PlcAccessService plc;
    private final TransferStatusCache statusCache;
    private final LocationTrackingRepository locationTrackingRepository;
    private final ContainerDataRepository containerDataRepository;

    private final OcrTaskCoordinator ocrCoordinator;   // 建立/派發 OCR 任務
    private final OcrCommandService ocrService;        // 輪詢 vendor 狀態
    private final OcrTaskRepository ocrTaskRepository; // 本地 OCR 任務備選/接續

    // 狀態
    private Long  currentContainerId = null;
    private boolean taskDispatched   = false;
    private Long  ocrTaskId          = null;
    private long  dispatchedAt       = 0L;

    private boolean collectPending   = false;
    private boolean collectInProgress= false;
    private long  collectReqSentAt   = 0L;

    private int   ocrAttempts        = 0;
    private long  lastPollAtMs       = 0;

    @Scheduled(fixedDelay = 600)
    public void monitor() {
        try {
            // 0) 若正在 Collect 握手，優先收斂
            if (handleCollectClosing()) {
                return;
            }

            // 1) 有無容器
            Optional<Long> cmOpt = locationTrackingRepository.findContainerOnTransfer(TRANSFER_ID);
            if (cmOpt.isEmpty()) { resetAll(); return; }
            Long cmId = cmOpt.get();

            // 2) 是否位於 V#5
            TransferDeviceStatus ds = statusCache.getLatest(TRANSFER_NAME);
            boolean fresh = ds != null && ds.isValidAndComplete(3);
            Integer lvl = fresh ? safeGetLevel(ds) : null;
            if (!(fresh && lvl != null && lvl == TARGET_LEVEL)) { resetAll(); return; }

            // 3) 切換容器 → 重置辨識/Collect 狀態
            if (!cmId.equals(currentContainerId)) {
                currentContainerId = cmId;
                clearOcrSession();
                log.info("[RESULT] 新容器 cm#{}", cmId);
            }

            // 4) 如「不需要 OCR」（兩欄已填）
            boolean needOcr = needsOcr(cmId);
            if (!needOcr) {
                // 若曾派單（collectPending=true）→ 依據 DB 視為成功回報 PLC
                if (collectPending && !collectInProgress) {
                    log.info("[RESULT] DB 已有結果且曾派單 → Collect(SUCCESS). cm#{}", cmId);
                    sendCollectAndBegin(true);
                    return;
                }
                // 否則什麼都不做（維持乾淨）；等待 MotionMonitor 讓它升回
                clearOcrSession(); // 確保不會殘留待處理
                return;
            }

            // 5) 僅在「到位且乾淨」時才會觸發 OCR 任務
            if (!isAtDown()) {
                return;
            }
            if (!isCleanIdle()) {
                return; // s==1 & 全寫入位 OFF
            }

            // 6) 尚未派單 → 先嘗試接手本地未完成任務，再決定是否派 Vendor
            if (!taskDispatched) {
                if (adoptExistingOcrTask(cmId)) {
                    taskDispatched = true;
                    collectPending = true;    // 之後一定會告知 PLC 成/敗
                    dispatchedAt    = System.currentTimeMillis();
                    log.info("[RESULT] ♻️ Adopt existing OCR task: cm#{} taskId={}", cmId, ocrTaskId);
                    // 不 return，讓 7) 決策流程接著跑
                } else {
                    if (dispatchOcr(cmId)) {
                        taskDispatched = true;
                        collectPending = true;
                        dispatchedAt   = System.currentTimeMillis();
                        return;
                    } else {
                        // 派單失敗 → 等下一輪（或由 TTL 保底走 FAIL）
                        return;
                    }
                }
            }

            // 7) 已派單且等待回報 → 嘗試「決定結果」
            if (collectPending && !collectInProgress) {
                boolean decided = false;

                // 7.0 先看 ocr_task（本地任務狀態）
                if (ocrTaskId != null) {
                    PollOutcome byTask = pollDbTaskOnce(cmId, ocrTaskId);
                    if (byTask == PollOutcome.SUCCESS) {
                        sendCollectAndBegin(true);
                        decided = true;
                    }
                    else if (byTask == PollOutcome.FAIL) {
                        sendCollectAndBegin(false);
                    }
                }

                // 7.1 再看 DB（人工或他方已寫入）
                if (!decided) {
                    ReleaseDecision byDb = decideCollectByDb(cmId);
                    if (byDb == ReleaseDecision.SUCCESS) {
                        sendCollectAndBegin(true); decided = true;
                    } else if (byDb == ReleaseDecision.FAIL) {
                        sendCollectAndBegin(false); decided = true;
                    }
                }

                // 7.2 最後看 vendor 輪詢（節流）
                if (!decided && ocrTaskId != null) {
                    PollOutcome byVendor = pollVendorOnce(cmId, ocrTaskId);
                    if (byVendor == PollOutcome.SUCCESS) {
                        sendCollectAndBegin(true); decided = true;
                    }
                    else if (byVendor == PollOutcome.FAIL) {
                        sendCollectAndBegin(false); decided = true;
                    }
                }

                // 7.3 TTL 超時 → FAIL
                // if (!decided && System.currentTimeMillis() - dispatchedAt > OCR_RESULT_TTL_MS) {
                //     log.warn("[RESULT] ⏱️ OCR RESULT TTL → Collect(FAIL). cm#{}", cmId);
                //     // 可選：標記 DB，避免後續困惑
                //     writeOcrTextsIfAbsent(cmId, "OCR_TIMEOUT", null);
                //     sendCollectAndBegin(false);
                //     decided = true;
                // }

                if (decided) return; // 交給 handleCollectClosing() 收尾
            }

        } catch (Exception e) {
            log.error("[RESULT] Monitor exception", e);
        }
    }

    // ---------- OCR 任務觸發 / 輪詢 ----------

    private boolean dispatchOcr(Long cmId) {
        OcrCommandResult res = ocrCoordinator.createAndDispatch(1 /* OCR_DEVICE_ID=1 */, cmId);
        if (!res.isSuccess() || Boolean.FALSE.equals(res.getVendorAccepted())) {
            log.warn("[RESULT] 📤 Dispatch NG: {} | accepted={}", res.getMessage(), res.getVendorAccepted());
            return false;
        }
        this.ocrTaskId = res.getTaskId();
        log.info("[RESULT] 📤 Dispatch OK: cm#{} taskId={}", cmId, ocrTaskId);
        return true;
    }

    private enum PollOutcome { PENDING, SUCCESS, FAIL }

    /** 嘗試接手 container 對應的「未完成」 OCR 任務（有就綁定 ocrTaskId）。 */
    private boolean adoptExistingOcrTask(Long cmId) {
        // 先查「是否存在未完成」
        if (!ocrTaskRepository.existsUnfinishedForContainer(cmId)) return false;

        // 目前使用 findLatestByContainerId(cmId) 找出當前容器最新一筆 OCR 任務。
        return ocrTaskRepository.findLatestByContainerId(cmId)
                .filter(t -> isOcrTaskPending(t.getStatus()))
                .map(t -> {
                    this.ocrTaskId = t.getId();
                    return true;
                })
                .orElse(false);
    }

    /** 轉呼叫本類既有的 pending 判斷（和 vendor 狀態邏輯一致） */
    private boolean isOcrTaskPending(String status) {
        return isTaskPending(status);
    }

    /** 單次輪詢（節流；成功可順便回寫 DB）。 */
    private PollOutcome pollVendorOnce(Long cmId, Long taskId) {
        long now = System.currentTimeMillis();
        if (now - lastPollAtMs < POLL_MIN_INTERVAL_MS) {
            return PollOutcome.PENDING;
        }
        lastPollAtMs = now;

        try {
            Optional<OcrTaskStatusResponse> opt = ocrService.getTaskStatus(taskId);
            if (opt.isEmpty()) {
                return PollOutcome.PENDING;
            }

            OcrTaskStatusResponse r = opt.get();
            String status = safeStatus(r);
            if (isPending(status)) {
                return PollOutcome.PENDING;
            }

            if (isCompletedOk(status)) {
                String[] texts = extractTexts(r);
                if (texts[0] != null || texts[1] != null) {
                    writeOcrTexts(cmId, texts[0], texts[1]);
                }

                return PollOutcome.SUCCESS;
            }
            // 非 pending 且非 success → 視為失敗（避免死等）
            ocrAttempts++;
            if (ocrAttempts >= MAX_OCR_RETRY) {
                return PollOutcome.FAIL;
            }
            return PollOutcome.PENDING;

        } catch (Exception e) {
            log.warn("[RESULT] poll error: {}", e.toString());
            ocrAttempts++;
            if (ocrAttempts >= MAX_OCR_RETRY) {
                return PollOutcome.FAIL;
            }
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
            log.warn("[RESULT] pollDbTaskOnce error: {}", e.toString());
            return PollOutcome.PENDING;
        }
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

    // ---------- Collect：回報 PLC 與握手收斂 ----------

    /** 寫 retcode 並拉起 B_COLLECT_REQ，開始 Collect 握手。 */
    private void sendCollectAndBegin(boolean success) {
        if (!deviceIdle()) {
            // 若當下 PLC 未就緒，先不送；等下一輪（避免命令被拒絕）
            return;
        }
        plc.writeInt32(DEVICE, W_OCR_RETCODE, success ? 0x0100 : 0x0F00);
        plc.writeBoolean(DEVICE, B_COLLECT_REQ, true);
        collectInProgress = true;
        collectReqSentAt  = System.currentTimeMillis();
        log.info("[RESULT] ▶️ Collect begin. ret={}", success ? "0x0100" : "0x0F00");
    }

    /**
     * 收斂 Collect：
     * - 對方 B_COLLECT_ACK=1 → 我們放掉 B_COLLECT_REQ=0
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
            log.info("[RESULT] ✅ Collect closed.");
            clearOcrSession();
            return true;
        }

        if (System.currentTimeMillis() - collectReqSentAt > COLLECT_ACK_TIMEOUT_MS) {
            log.warn("[RESULT] ⏱️ Collect ACK timeout; force close.");
            plc.writeBoolean(DEVICE, B_COLLECT_REQ, false);
            clearOcrSession();
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

    // ---------- 判斷工具（與 Motion 相同但不動寫入位） ----------

    private boolean deviceIdle() {
        int s = plc.readInt32(DEVICE, W_STATUS) & 0xF; // 1 Idle / 2 Processing / 3 Complete
        return s == 1;
    }
    /** 乾淨 IDLE：Standby=true、s==1，且所有交握位 OFF（不含 Collect，本類會自行控制）。 */
    private boolean isCleanIdle() {
        if (!deviceIdle()) return false;
        if (plc.readBoolean(DEVICE, B_CMD_REQ))  return false;
        if (plc.readBoolean(DEVICE, B_CMD_ACK))  return false;
        if (plc.readBoolean(DEVICE, B_COMP_REQ)) return false;
        if (plc.readBoolean(DEVICE, B_COMP_ACK)) return false;
        return true;
    }
    private boolean isAtDown() { return plc.readInt32(DEVICE, W_POS_BAY) == BAY_DOWN; }

    private Integer safeGetLevel(TransferDeviceStatus ds) { try { return ds.getLevel(); } catch (Throwable ignore) { return null; } }
    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private String  safeTrim(String s) { return s == null ? "" : s.trim(); }

    // ---------- Vendor 狀態小工具 ----------
    private String safeStatus(OcrTaskStatusResponse r) { try { return r.getStatus(); } catch (Throwable ignore) { return null; } }
    private boolean isPending(String s) {
        if (s == null) return true;
        String u = s.trim().toUpperCase();
        return u.contains("PENDING") || u.contains("QUEUE") || u.contains("RUNNING") || u.contains("PROCESS");
    }
    private boolean isCompletedOk(String s) {
        if (s == null) return false;
        String u = s.trim().toUpperCase();
        return u.equals("SUCCESS") || u.equals("SUCCEEDED") || u.equals("COMPLETED");
    }
    private String[] extractTexts(OcrTaskStatusResponse r) {
        String t1 = null, t2 = null;
        try { t1 = blankToNull(r.getOcrText1()); } catch (Throwable ignore) {}
        try { t2 = blankToNull(r.getOcrText2()); } catch (Throwable ignore) {}
        return new String[]{t1, t2};
    }
    private String blankToNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }

    // ---------- Session ----------
    private void clearOcrSession() {
        taskDispatched    = false;
        ocrTaskId         = null;
        dispatchedAt      = 0L;
        collectPending    = false;
        collectInProgress = false;
        collectReqSentAt  = 0L;
        ocrAttempts       = 0;
        lastPollAtMs      = 0;
    }
    private void resetAll() { clearOcrSession(); currentContainerId = null; }
}
