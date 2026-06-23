package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerAttr;
import com.czkuo.rdf88701.infra.entity.GripperRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * GP1RequestGenerator
 * - 來源：Site#3
 * - 目標：Site#4
 * 規則：
 *   1) 若夾爪上已有容器 且 Site#4 為空 → 建立 DROP → Site#4
 *   2) 若 Site#4 有容器 → 不建單
 *   3) 若 Site#3 有容器 → 建立 PICK Site#3 → Site#4
 */
@Slf4j
@Component("GP1")
@RequiredArgsConstructor
public class GP1RequestGenerator implements GripperRequestGenerator {

    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final GripperStatusCache statusCache; // 以快取取得 PLC 狀態

    private static final String SOURCE_NAME = "Site#3";
    private static final String TARGET_NAME = "Site#4";

    private static final int SOURCE_LEVEL = 3;
    private static final int TARGET_LEVEL = 4;

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // 1) 若已有未完成請求/任務 → 略過
        if (requestRepository.existsUnfinishedRequestForDevice(gripperId)
                || taskRepository.existsUnfinishedTaskForGripper(gripperId)) {
            //log.debug("[GP1] Gripper#{} 已有未完成請求或任務，略過", gripperId);
            return Optional.empty();
        }

        String gripperName = "Gripper#" + gripperId;
        GripperDeviceStatus ds = statusCache.getLatest(gripperName);

        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP1] GP1 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!ds.isTransferStandby()) {
            //log.debug("[GP1] GP1 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        Integer level = safeGetLevel(ds);

        // 若你們有 isTransferStandby()，當作輔助
        boolean atStandby = level != null && level == SOURCE_LEVEL;
        boolean atFeed    = level != null && level == TARGET_LEVEL;

        // 2) 夾爪上是否已有容器？若有且目標為空 → DROP
        Optional<Long> containerOnGripper = locationTrackingRepository.findContainerOnGripper(gripperId);
        if (containerOnGripper.isPresent()) {
            Long containerId = containerOnGripper.get();
            boolean targetOccupied = locationTrackingRepository.hasContainerAtLocationName(TARGET_NAME);
            if (!targetOccupied) {
                return createRequest(gripperId, "DROP", null, TARGET_NAME, containerId);
            } else {
                //log.debug("[GP1] 夾爪有容器但 {} 已有容器，略過", TARGET_NAME);
                return Optional.empty();
            }
        }

        // 3) 目標有容器 → 不建單(除非不在待命位)
        if (locationTrackingRepository.hasContainerAtLocationName(TARGET_NAME)) {

            if (!atStandby) {
                return createRequest(gripperId, "MOVE", null, SOURCE_NAME, null);
            }

            //log.debug("[GP1] 目標 {} 已有容器，略過", TARGET_NAME);
            return Optional.empty();
        }

        // 4) 來源有容器 → 建立 PICK Site#3 → Site#4
        Optional<Long> containerAtSource = locationTrackingRepository.findContainerAtLocationName(SOURCE_NAME);
        if (containerAtSource.isEmpty()) {

            if (!atStandby) {
                return createRequest(gripperId, "MOVE", null, SOURCE_NAME, null);
            }

            //log.debug("[GP1] 來源 {} 無容器，略過", SOURCE_NAME);
            return Optional.empty();
        }

        return createRequest(gripperId, "PICK", SOURCE_NAME, TARGET_NAME, containerAtSource.get());
    }

    /** 以名稱查 id 後建立請求（與你 GP2 的風格一致） */
    private Optional<Long> createRequest(Long gripperId, String taskType, String sourceName, String targetName, Long containerMainId) {
        Long sourceId = null;
        if (sourceName != null) {
            sourceId = locationId(sourceName);
        }

        Long targetId = locationId(targetName);

        GripperRequest request = new GripperRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setGripperId(gripperId);
        request.setTaskType(taskType);
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        request.setSourceLocationId(sourceId);
        request.setTargetLocationId(targetId);
        request.setSourceLocationName(sourceName);
        request.setTargetLocationName(targetName);
        request.setContainerMainId(containerMainId);

        Double trayThickness = resolveTrayThicknessSafe(containerMainId);
        if (trayThickness == null) {
            return Optional.empty();
        }
        request.setTargetHeightMm(BigDecimal.valueOf(trayThickness));
        request.setLayerCount(0);

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[GP1] 建立 GripperRequest 成功: {} → {} [{}], containerId={}",
                    sourceName, targetName, taskType, containerMainId);
            return Optional.of(request.getId());
        } else {
            log.warn("[GP1] 建立 GripperRequest 失敗 [{}]", taskType);
            return Optional.empty();
        }
    }

    /** 以 id 直接建立請求（保留 overload） */
    private Optional<Long> createRequest(Long gripperId, String taskType, Long sourceId, Long targetId, Long containerMainId) {
        GripperRequest request = new GripperRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setGripperId(gripperId);
        request.setTaskType(taskType);
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());
        request.setSourceLocationId(sourceId);
        request.setTargetLocationId(targetId);
        request.setContainerMainId(containerMainId);

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[GP1] 建立 GripperRequest 成功: {} → {} [{}], containerId={}",
                    sourceId, targetId, taskType, containerMainId);
            return Optional.of(request.getId());
        } else {
            log.warn("[GP1] 建立 GripperRequest 失敗 [{}]", taskType);
            return Optional.empty();
        }
    }

    /** 嘗試取得目前 Level；若你的 DTO 名稱不同，直接把這裡改成對應欄位即可 */
    private Integer safeGetLevel(GripperDeviceStatus ds) {
        try {
            return ds.getLevel(); // ← 若你的型別是 getCurrentLevel() 或 getZLevel()，改這一行
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 讀取單片托盤厚度（mm）；來源 container_attr.key=tray_thickness_mm。格式寬鬆；錯誤回 null。 */
    private Long locationId(String name) {
        return GripperLocationCache.requireLocationId(locationPointRepository, name);
    }

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

    /** 寬鬆數值解析並要求正數：允許 "5.62", "5,62", "5.62mm"；非正或格式不對回 null。 */
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
}
