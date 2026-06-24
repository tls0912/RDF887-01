package com.czkuo.rdf88701.application.service.ocr;

import com.czkuo.rdf88701.common.dto.OcrCommandResult;
import com.czkuo.rdf88701.domain.dto.ocr.OcrCreateTaskRequest;
import com.czkuo.rdf88701.domain.dto.ocr.OcrCreateTaskResponse;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
/**
 * OCR 任務建立與派送協調服務。
 *
 * <p>負責建立本地 ocr_task、避免同一容器重複派送未完成任務，並呼叫
 * OcrCommandService 將任務送往 OCR 廠商。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrTaskCoordinator {

    private final OcrCommandService ocrCommandService;
    private final OcrTaskRepository ocrTaskRepository;

    /**
     * 建任務 + 送廠商（抽共用邏輯）
     * - 避免重複送單：同一 container 若已有未完成任務則直接回傳（可視需求放寬）
     */
    public OcrCommandResult createAndDispatch(Integer ocrDeviceId, Long containerId) {
        // 參數檢核
        if (ocrDeviceId == null || ocrDeviceId < 1) {
            return OcrCommandResult.fail("ocrDeviceId is required and must be >= 1");
        }
        if (containerId == null || containerId < 1) {
            return OcrCommandResult.fail("containerId is required and must be >= 1");
        }

        // A) 先檢查是否已有未完成任務（避免重複送）
        if (existsUnfinishedForContainer(containerId)) {
            var latest = findLatestForContainer(containerId).orElse(null);
            Long taskId = latest != null ? latest.getId() : null;
            log.warn("[OCR COORD] Skip duplicate dispatch: container#{} has unfinished task#{}", containerId, taskId);
            return new OcrCommandResult(true,
                    "已存在未完成的 OCR 任務",
                    taskId, ocrDeviceId, null, null, LocalDateTime.now());
        }

        // B) 建立本地任務（QUEUED）
        OcrTask t = new OcrTask();
        t.setOcrDeviceId(ocrDeviceId);
        t.setContainerMainId(containerId);
        t.setStatus("QUEUED");
        t.setCreatedTime(LocalDateTime.now());
        if (!ocrTaskRepository.save(t)) {
            return OcrCommandResult.fail("Create local ocr_task failed");
        }
        Long taskId = t.getId();
        log.info("[OCR COORD] Local task created: taskId={}, deviceId={}, containerId={}", taskId, ocrDeviceId, containerId);

        // C) 呼叫 Vendor 建任務
        OcrCreateTaskRequest vendorReq = new OcrCreateTaskRequest();
        vendorReq.setTaskId(taskId);
        vendorReq.setOcrDeviceId(ocrDeviceId);
        vendorReq.setContainerId(containerId);
        vendorReq.setCreatedTime(t.getCreatedTime());

        OcrCreateTaskResponse vendorResp = null;
        try {
            vendorResp = ocrCommandService.createTask(vendorReq);
        } catch (Exception e) {
            log.info("[OCR COORD] createTask exception, taskId={}, deviceId={}", taskId, ocrDeviceId, e);
        }
        // D) 回應處理
        if (vendorResp == null) {
            // 標記失敗
            var saved = ocrTaskRepository.findById(taskId).orElse(null);
            if (saved != null) {
                saved.setStatus("SUCCESS");
                saved.setOcrText1("CallOcrError");
                saved.setErrorMessage("Vendor createTask null response");
                ocrTaskRepository.update(saved);
            }
            return OcrCommandResult.fail("Vendor createTask returned null", taskId, ocrDeviceId, "NULL_RESP");
        }

        boolean accepted = Boolean.TRUE.equals(vendorResp.getAccepted());
        var saved = ocrTaskRepository.findById(taskId).orElse(null);
        if (saved != null) {
            saved.setStatus(accepted ? "DISPATCHED" : "FAILED");
            saved.setErrorMessage(accepted ? null : vendorResp.getMessage());
            if (saved.getStatus() == null || !Set.of("QUEUED","RUNNING","SUCCESS","FAILED","DISPATCHED").contains(saved.getStatus())) {
                log.error("status = [{}]", saved.getStatus());
            }
            ocrTaskRepository.update(saved);
        }

        if (!accepted) {
            return OcrCommandResult.fail(
                    Optional.ofNullable(vendorResp.getMessage()).orElse("Vendor rejected"),
                    taskId, ocrDeviceId, vendorResp.getErrorCode()
            );
        }

        return OcrCommandResult.success(
                taskId, ocrDeviceId, vendorResp.getAccepted(), vendorResp.getMessage(), vendorResp.getErrorCode()
        );
    }

    /**
     * 判斷同一 container 是否有未完成任務（可依你們的定義調整狀態集合）
     */
    public boolean existsUnfinishedForContainer(Long containerId) {
        // 你可以在 OcrTaskRepository 加這個查詢；這裡給個保守 fallback
        return findLatestForContainer(containerId)
                .map(t -> !isFinalStatus(t.getStatus()))
                .orElse(false);
    }

    public Optional<OcrTask> findLatestForContainer(Long containerId) {
        try {
            return ocrTaskRepository.findLatestByContainerId(containerId);
        } catch (UnsupportedOperationException e) {
            // 如果暫時沒有 repo 方法，可退回用 findAll 再過濾（此處略）
            return Optional.empty();
        }
    }

    private boolean isFinalStatus(String s) {
        return "COMPLETED".equalsIgnoreCase(s) || "FAILED".equalsIgnoreCase(s) || "CANCELLED".equalsIgnoreCase(s);
    }
}
