package com.czkuo.rdf88701.application.service.ocr;

import com.czkuo.rdf88701.domain.dto.ocr.*;
import com.czkuo.rdf88701.infra.ocr.OcrVendorHttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * OcrCommandService
 * <p>
 * MCS → OCR 指令發送邏輯：
 * - 直接呼叫 OCR 廠商 WebAPI（透過 OcrVendorHttpClient）
 * <p>
 * 便捷方法：
 * - createTask(...) 簡化參數版（taskId / deviceId / containerId / createdTime）
 * - createTaskAndProbe(...) 送出後立即探測一次任務狀態（非必要，但除錯/驗證很有用）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrCommandService {

    private final OcrVendorHttpClient client;

    /** 建立 OCR 任務（POST /api/v1/ocr-tasks） */
    public OcrCreateTaskResponse createTask(OcrCreateTaskRequest req) {
        Objects.requireNonNull(req, "req must not be null");
        Objects.requireNonNull(req.getTaskId(), "taskId must not be null");
        Objects.requireNonNull(req.getOcrDeviceId(), "ocrDeviceId must not be null");
        Objects.requireNonNull(req.getContainerId(), "containerId must not be null");

        log.info("[OCR SVC] createTask: taskId={}, deviceId={}, containerId={}, createdTime={}",
                req.getTaskId(), req.getOcrDeviceId(), req.getContainerId(), req.getCreatedTime());
        return client.createTask(req);
    }

    /** 建立 OCR 任務（便捷版） */
    public OcrCreateTaskResponse createTask(Long taskId, Integer ocrDeviceId, Long containerId, LocalDateTime createdTime) {
        OcrCreateTaskRequest req = new OcrCreateTaskRequest();
        req.setTaskId(Objects.requireNonNull(taskId, "taskId"));
        req.setOcrDeviceId(Objects.requireNonNull(ocrDeviceId, "ocrDeviceId"));
        req.setContainerId(Objects.requireNonNull(containerId, "containerId"));
        req.setCreatedTime(createdTime != null ? createdTime : LocalDateTime.now());
        return createTask(req);
    }

    /**
     * 建立任務並「立即探測一次狀態」
     * - 成功建立後立刻 GET /ocr-tasks/{taskId}
     * - 供上層快速確認（有些廠商為最終一致，若當下還查不到會回 Optional.empty()）
     */
    public OcrCreateTaskResponse createTaskAndProbe(Long taskId, Integer ocrDeviceId, Long containerId, LocalDateTime createdTime) {
        OcrCreateTaskResponse created = createTask(taskId, ocrDeviceId, containerId, createdTime);
        client.getTaskStatus(taskId).ifPresentOrElse(
                st -> log.info("[OCR SVC] probe status after create: taskId={}, status={}", taskId, st.getStatus()),
                () -> log.warn("[OCR SVC] probe status after create: taskId={} not visible yet (vendor eventual consistency?)", taskId)
        );
        return created;
    }

    /** 查任務狀態（GET /api/v1/ocr-tasks/{taskId}） */
    public Optional<OcrTaskStatusResponse> getTaskStatus(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        log.info("[OCR SVC] getTaskStatus: taskId={}", taskId);
        return client.getTaskStatus(taskId);
    }

    /** 取任務圖片（單張，GET /api/v1/ocr-tasks/{taskId}/image） */
    public Optional<OcrTaskImageResponse> getTaskImage(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        log.info("[OCR SVC] getTaskImage: taskId={}", taskId);
        return client.getTaskImage(taskId);
    }

    /** 取任務圖片（多張，GET /api/v1/ocr-tasks/{taskId}/images） */
    public Optional<OcrTaskImagesResponse> getTaskImages(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        log.info("[OCR SVC] getTaskImages: taskId={}", taskId);
        return client.getTaskImages(taskId);
    }

    /** 查設備狀態（GET /api/v1/ocr-devices/{id}/status） */
    public Optional<OcrDeviceStatusResponse> getDeviceStatus(Integer ocrDeviceId) {
        Objects.requireNonNull(ocrDeviceId, "ocrDeviceId");
        log.info("[OCR SVC] getDeviceStatus: deviceId={}", ocrDeviceId);
        return client.getDeviceStatus(ocrDeviceId);
    }

    /** 查設備警報（GET /api/v1/ocr-devices/{id}/alarms） */
    public List<OcrAlarmItem> getDeviceAlarms(Integer ocrDeviceId) {
        Objects.requireNonNull(ocrDeviceId, "ocrDeviceId");
        log.info("[OCR SVC] getDeviceAlarms: deviceId={}", ocrDeviceId);
        return client.getDeviceAlarms(ocrDeviceId);
    }
}
