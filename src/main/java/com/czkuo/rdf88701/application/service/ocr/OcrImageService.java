package com.czkuo.rdf88701.application.service.ocr;

import com.czkuo.rdf88701.common.util.ImageUtils;
import com.czkuo.rdf88701.domain.dto.ocr.OcrTaskImagesResponse;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.OcrTaskRepository;
import com.czkuo.rdf88701.infra.entity.OcrTask;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrImageService {

    private final OcrCommandService ocrService;
    private final OcrTaskRepository ocrTaskRepository;
    private final LocationTrackingRepository locationTrackingRepository;

    /** 影像儲存根目錄（可環境變數/設定檔調） */
    @Value("${ocr.image-store-dir:/data/ocr}")
    private String imageStoreRoot;

    /** 下載時是否落檔；若設 false 則只回 data URLs */
    @Value("${ocr.image-save-to-disk:true}")
    private boolean saveToDisk;

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Data @AllArgsConstructor
    public static class OcrImagesBundle {
        /** 依 index 排序的完整檔案路徑（可能為空清單） */
        private List<String> filePaths;
        /** 依 index 排序的 Data URL（image/jpeg 等） */
        private List<String> dataUrls;
        /** 來源 taskId（除錯/追蹤方便） */
        private Long taskId;
    }

    /** 依容器取「最新 OCR 任務」多圖 */
    public Optional<OcrImagesBundle> getLatestImagesForContainer(Long containerMainId) {
        return ocrTaskRepository.findLatestByContainerId(containerMainId)
                .map(OcrTask::getId)
                .flatMap(this::downloadImagesByTaskId);
    }

    /** 依站點（先找在位容器）→ 最新 OCR 任務 → 多圖 */
    public Optional<OcrImagesBundle> getLatestImagesForLocation(String locationName) {
        return locationTrackingRepository.findContainerAtLocationName(locationName)
                .flatMap(this::getLatestImagesForContainer);
    }

    /** 直接以 taskId 取多圖（必要時用） */
    public Optional<OcrImagesBundle> downloadImagesByTaskId(Long taskId) {
        try {
            var opt = ocrService.getTaskImages(taskId);
            if (opt.isEmpty()) {
                log.warn("[OCR IMG] taskId={} images not available", taskId);
                return Optional.empty();
            }
            OcrTaskImagesResponse resp = opt.get();
            var items = Optional.ofNullable(resp.getImages()).orElse(List.of());
            if (items.isEmpty()) {
                log.warn("[OCR IMG] taskId={} images empty", taskId);
                return Optional.empty();
            }

            // 依 index 排序
            var sorted = items.stream()
                    .sorted(Comparator.comparing(i -> Optional.ofNullable(i.getIndex()).orElse(0)))
                    .collect(Collectors.toList());

            List<String> filePaths = new ArrayList<>();
            List<String> dataUrls  = new ArrayList<>();

            // 目錄：{root}/task-{taskId}/
            Path taskDir = Paths.get(imageStoreRoot, "task-" + taskId);

            for (var item : sorted) {
                int idx = Optional.ofNullable(item.getIndex()).orElse(0);
                String base64 = item.getImageBase64();
                if (base64 == null || base64.isBlank()) continue;

                // 你供應商多半是 JPEG；如有 mime 欄位可自行判斷
                String mime   = "image/jpeg";
                String fname  = "img_" + idx + ".jpg";
                Path fpath    = taskDir.resolve(fname);

                if (saveToDisk) {
                    ImageUtils.base64ToFile(base64, fpath);
                    filePaths.add(fpath.toAbsolutePath().toString());
                }
                // 不管有沒有落檔，都提供 Data URL（上游 MQTT/S073 最好直接吃這個）
                if (saveToDisk) {
                    dataUrls.add(ImageUtils.fileToDataUrl(fpath));
                } else {
                    dataUrls.add(ImageUtils.toDataUrl(mime, ImageUtils.base64ToBytes(base64)));
                }
            }

            if (filePaths.isEmpty() && dataUrls.isEmpty()) {
                log.warn("[OCR IMG] taskId={} decoded none", taskId);
                return Optional.empty();
            }

            return Optional.of(new OcrImagesBundle(filePaths, dataUrls, taskId));

        } catch (Exception e) {
            log.error("[OCR IMG] taskId={} download failed: {}", taskId, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
