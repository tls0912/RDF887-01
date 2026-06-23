package com.czkuo.rdf88701.presentation.web.controller;

import com.czkuo.rdf88701.application.service.ocr.OcrCommandService;
import com.czkuo.rdf88701.application.service.ocr.OcrTaskCoordinator;
import com.czkuo.rdf88701.common.dto.OcrCommandResult;
import com.czkuo.rdf88701.common.dto.ResponseResult;
import com.czkuo.rdf88701.common.util.ImageUtils;
import com.czkuo.rdf88701.domain.dto.ocr.*;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCS → OCR 指令 API
 * - 對內/對外提供「建立任務 / 查任務 / 取圖片 / 查設備狀態 / 查警報」等呼叫入口
 */
@Slf4j
@RestController
@RequestMapping("/api/ocr/command")
@RequiredArgsConstructor
@Validated
public class OcrCommandApiController {

    private final OcrTaskCoordinator coordinator;
    private final OcrCommandService service; // 直接注入具體 service（內部再去呼叫 vendor http client）
    private final OcrTaskRepository ocrTaskRepository;

    /** 先在本地建立任務（自動產生 taskId），再發送 Vendor */
    @PostMapping("/create-and-dispatch")
    public ResponseResult<OcrCommandResult> createAndDispatch(
            @RequestBody @Valid OcrCreateTaskCommand req,
            @RequestHeader(value = "X-Request-Id", required = false) String reqId) {

        var result = coordinator.createAndDispatch(req.getOcrDeviceId(), req.getContainerId());
        return result.isSuccess() ? ResponseResult.ok(result) : ResponseResult.fail(result.getMessage());
    }

    /** 發送 OCR 任務（POST /dispatch-task）—若要「只轉送」且 taskId 已預建 */
    @PostMapping("/dispatch-task")
    public ResponseResult<OcrCreateTaskResponse> createTask(@RequestBody @Valid OcrCreateTaskRequest req) {
        final Long taskId = req.getTaskId();
        log.info("[OCR CMD] CreateTask: taskId={}, req={}", taskId, req);

        // 1) 先檢查本地是否已有 ocr_task（沒有就拒絕）
        if (taskId == null || ocrTaskRepository.findById(taskId).isEmpty()) {
            log.warn("[OCR CMD] CreateTask blocked: local ocr_task not found, taskId={}", taskId);
            return ResponseResult.fail("Local OCR task not found: " + taskId);
        }

        // 2) 若想限制只能在 QUEUED 狀態下送，打開這段
        var local = ocrTaskRepository.findById(taskId).get();
        if (!"QUEUED".equalsIgnoreCase(local.getStatus())) {
            return ResponseResult.fail("Task is not QUEUED, current=" + local.getStatus());
        }

        // 一致性保護：containerId/ocrDeviceId 與本地紀錄需一致
        if (req.getContainerId() == null || req.getContainerId() < 1) {
            return ResponseResult.fail("containerId is required and must be >= 1");
        }
        if (!req.getContainerId().equals(local.getContainerMainId())) {
            return ResponseResult.fail("containerId mismatch with local task");
        }
        if (req.getOcrDeviceId() == null || req.getOcrDeviceId() < 1) {
            return ResponseResult.fail("ocrDeviceId is required and must be >= 1");
        }
        if (!req.getOcrDeviceId().equals(local.getOcrDeviceId())) {
            return ResponseResult.fail("ocrDeviceId mismatch with local task");
        }

        // 時間欄位由伺服器主導：若 caller 帶了 createdTime 亦覆蓋為本地記錄
        req.setCreatedTime(local.getCreatedTime());

        // 3) 通過檢查才呼叫廠商
        OcrCreateTaskResponse resp = service.createTask(req);
        return ResponseResult.ok(resp);
    }

    /** 查任務狀態（GET /ocr-tasks/{taskId}） */
    @GetMapping("/task-status/{taskId}")
    public ResponseResult<OcrTaskStatusResponse> getTaskStatus(
            @PathVariable @NotNull @Min(1) Long taskId) {
        log.info("[OCR CMD] GetTaskStatus: taskId={}", taskId);
        Optional<OcrTaskStatusResponse> resp = service.getTaskStatus(taskId);
        return resp.map(ResponseResult::ok)
                .orElseGet(() -> ResponseResult.ok(null)); // 找不到時回 200+null（如要改為 fail 可告訴我）
    }

    /** 取任務圖片（GET /ocr-tasks/{taskId}/image） */
    @GetMapping("/task-image/{taskId}")
    public ResponseResult<OcrTaskImageResponse> getTaskImage(
            @PathVariable @NotNull @Min(1) Long taskId) {
        log.info("[OCR CMD] GetTaskImage: taskId={}", taskId);
        Optional<OcrTaskImageResponse> resp = service.getTaskImage(taskId);
        return resp.map(ResponseResult::ok)
                .orElseGet(() -> ResponseResult.ok(null));
    }

    /** 取任務圖片（多張，GET /ocr-tasks/{taskId}/images） */
    @GetMapping("/ocr-tasks/{taskId}/images")
    public ResponseResult<OcrTaskImagesResponse> getTaskImages(
            @PathVariable @NotNull @Min(1) Long taskId) {
        log.info("[OCR CMD] GetTaskImages: taskId={}", taskId);
        Optional<OcrTaskImagesResponse> resp = service.getTaskImages(taskId);
        return resp.map(ResponseResult::ok)
                .orElseGet(() -> ResponseResult.ok(null));
    }

    /** 查設備狀態（GET /ocr-devices/{ocrDeviceId}/status） */
    @GetMapping("/device-status/{ocrDeviceId}")
    public ResponseResult<OcrDeviceStatusResponse> getDeviceStatus(
            @PathVariable @NotNull @Min(1) Integer ocrDeviceId) {
        log.info("[OCR CMD] GetDeviceStatus: deviceId={}", ocrDeviceId);
        Optional<OcrDeviceStatusResponse> resp = service.getDeviceStatus(ocrDeviceId);
        return resp.map(ResponseResult::ok)
                .orElseGet(() -> ResponseResult.ok(null));
    }

    /** 查設備警報清單（GET /ocr-devices/{ocrDeviceId}/alarms） */
    @GetMapping("/device-alarms/{ocrDeviceId}")
    public ResponseResult<List<OcrAlarmItem>> getDeviceAlarms(
            @PathVariable @NotNull @Min(1) Integer ocrDeviceId) {
        log.info("[OCR CMD] GetDeviceAlarms: deviceId={}", ocrDeviceId);
        List<OcrAlarmItem> list = service.getDeviceAlarms(ocrDeviceId);
        return ResponseResult.ok(list);
    }

    /** 多張圖片落檔到「指令路徑」：{base}/task-{taskId}/img_{index}.{ext} */
    @PostMapping("/ocr-tasks/{taskId}/images/save")
    public ResponseResult<SaveImagesResult> saveTaskImagesToCommandPath(
            @PathVariable @NotNull @Min(1) Long taskId,
            @RequestBody @Valid SaveImagesRequest req) {

        log.info("[OCR CMD] SaveTaskImages: taskId={}, req={}", taskId, req);

        // 1) 拉多張圖
        var opt = service.getTaskImages(taskId);
        if (opt.isEmpty() || opt.get().getImages() == null || opt.get().getImages().isEmpty()) {
            return ResponseResult.fail("No images available for taskId=" + taskId);
        }

        var resp = opt.get();
        var items = resp.getImages().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getIndex() == null ? 0 : i.getIndex()))
                .toList();

        // 2) 目標資料夾：{base}/{{taskId}}
        String sub     = "task-" + taskId;
        String ext     = pickExt(req.getImageFormat()); // "jpg" | "png" | ...
        boolean overwrite = req.getOverwrite() != null ? req.getOverwrite() : false;

        Path dir = Paths.get(commandImageDir, sub);
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            log.error("[OCR CMD] mkdirs failed: {}", dir, e);
            return ResponseResult.fail("Create directory failed: " + dir);
        }

        // 3) 逐張落檔
        List<String> saved = new ArrayList<>();
        for (var it : items) {
            int idx = it.getIndex() == null ? saved.size() : it.getIndex();
            String fname = "img_" + idx + "." + ext;
            Path file = dir.resolve(fname);

            if (!overwrite && Files.exists(file)) {
                log.info("[OCR CMD] skip existing file (overwrite=false): {}", file);
                saved.add(file.toAbsolutePath().toString());
                continue;
            }
            try {
                ImageUtils.base64ToFile(it.getImageBase64(), file);
                saved.add(file.toAbsolutePath().toString());
            } catch (Exception e) {
                log.warn("[OCR CMD] save image failed (index={}): {}", idx, e.toString());
            }
        }

        SaveImagesResult result = new SaveImagesResult();
        result.setBaseDir(dir.toAbsolutePath().toString());
        result.setFiles(saved);
        result.setCount(saved.size());
        result.setTaskId(taskId);

        return ResponseResult.ok(result);
    }

    // ====== 設定：指令影像根目錄（可在 application.yml 覆蓋），預設 /data/ocr ======
    @Value("${ocr.command-image-dir:/data/ocr}")
    private String commandImageDir;

    // ====== 請放在 Controller 內部的簡單 helper / DTO ======

    private static String sanitize(String s) {
        // 只留字母數字與_-，避免注入奇怪路徑
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private static String pickExt(String fmt) {
        if (fmt == null || fmt.isBlank()) return "jpg";
        String f = fmt.trim().toLowerCase();
        return switch (f) {
            case "jpg", "jpeg" -> "jpg";
            case "png" -> "png";
            case "bmp" -> "bmp";
            case "gif" -> "gif";
            case "webp" -> "webp";
            case "tif", "tiff" -> "tif";
            default -> "jpg";
        };
    }

    @lombok.Data
    public static class SaveImagesRequest {
        /** 目標指令代碼（例：S073）；空白則落到 misc */
        private String cmdId;
        /** 任務 TID（例：yyyyMMddHHmmssSSS）；空白則用 task-{taskId} */
        private String tid;
        /** 目標影像格式（jpg/png/...）；預設 jpg（僅決定副檔名，不轉檔） */
        private String imageFormat;
        /** 若檔案已存在是否覆蓋；預設 false */
        private Boolean overwrite;
    }

    @lombok.Data
    public static class SaveImagesResult {
        private Long taskId;
        private String baseDir;
        private java.util.List<String> files;
        private int count;
    }

}
