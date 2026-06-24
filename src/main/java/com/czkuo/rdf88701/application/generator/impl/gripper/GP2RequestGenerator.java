package com.czkuo.rdf88701.application.generator.impl.gripper;

import com.czkuo.rdf88701.application.generator.GripperRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * GP2RequestGenerator
 * 規則：
 * - DROP：若夾爪上有容器 → 僅在 Site#21 無帳 且 WB#7 無未完成請求/任務 時，建立 DROP → Site#21
 * - PICK：夾爪空手時，僅在 TR#7「DB 有帳」且「位置=VIRTUAL#11(Level)」時，建立
 * PICK：VIRTUAL#11 → Site#21
 * - 測高：改在 Site#21 進行。
 * 當夾爪空手且 Site#21 有容器，且該容器尚未 verified_quantity，
 * 若紅外線不忙 → 先送量測請求；本輪不建單。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("GP2")
@RequiredArgsConstructor
public class GP2RequestGenerator implements GripperRequestGenerator {

    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final ContainerAttrRepository containerAttrRepository;
    private final LocationPointRepository locationPointRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final DeviceProcessStateReader stateReader;

    // 測高需要的 Repository
    private final ContainerDataRepository containerDataRepository;
    private final InfraredRequestRepository infraredRequestRepository;
    private final InfraredTaskRepository infraredTaskRepository;

    // 以快取取得 PLC 狀態
    private final GripperStatusCache statusCache;

    // 檢查 TR7 Level
    private final TransferStatusCache transferStatusCache;

    // DROP 前確認 WB7 無需求任務
    private final WorkingBeamRequestRepository workingBeamRequestRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;

    // ===== 名稱常數 =====
    private static final String SOURCE_NAME_VISUAL = "VIRTUAL#11";
    private static final String TARGET_NAME = "Site#21";

    // ===== DB/PLC 判斷常數 =====
    private static final long TRANSFER7_ID = 7L;
    private static final String TRANSFER7_NAME = "Transfer#7";
    private static final int VIRTUAL11_LEVEL = 211;
    private static final long WORKING_BEAM7_ID = 7L;
    private static final long INFRARED_ID = 2L;

    @Override
    public Optional<Long> generateRequest(Long gripperId) {
        // 0) 全域忙碌擋（你要保留）：IR/Gripper 任一忙碌就整支 GP2 跳過
        if (!deviceIsRun("拆併區"))
            return Optional.empty();

        GripperGenerationContext generationContext = generationContext();
        if (generationContext.infraredBusy(INFRARED_ID) || generationContext.gripperBusy(gripperId)) {
            //log.debug("[GP2] 忙碌互斥（IR/Gripper），略過。");
            return Optional.empty();
        }

        String gripperName = "Gripper#" + gripperId;
        GripperDeviceStatus ds = statusCache.getLatest(gripperName);

        boolean fresh = ds != null && ds.isValidAndComplete(3);
        if (!fresh) {
            //log.debug("[GP2] GP2 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!ds.isTransferStandby()) {
            //log.debug("[GP2] GP2 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // 1) 先處理「夾爪持物」→ 優先 DROP（手上有東西不能測高）
        Optional<Long> containerOnGripper = locationTrackingRepository.findContainerOnGripper(gripperId);
        if (containerOnGripper.isPresent()) {
            Long containerId = containerOnGripper.get();

            // Site#21 必須為空
            if (locationTrackingRepository.hasContainerAtLocationName(TARGET_NAME)) {
                //log.debug("[GP2] 目標 {} 已有容器，無法 DROP", TARGET_NAME);
                return Optional.empty();
            }
            // WB7 不得忙
            boolean wb7Busy = generationContext.workingBeamBusy(WORKING_BEAM7_ID);
            if (wb7Busy) {
                //log.debug("[GP2] WB#7 忙，暫不允許 DROP → {}", TARGET_NAME);
                return Optional.empty();
            }

            // DROP（來源=夾爪）
            return createRequestByName(gripperId, "DROP", null, TARGET_NAME, containerId);
        }

        // 2) 夾爪空手才允許做「Site#21 測高」
        if (locationTrackingRepository.hasContainerAtLocationName(TARGET_NAME)) {
            Optional<Long> c21Opt = locationTrackingRepository.findContainerAtLocationName(TARGET_NAME);
            if (c21Opt.isPresent()) {
                Long c21 = c21Opt.get();
                if (!hasVerifiedQuantity(c21)) {
                    // 這裡不再檢查 infraredBusy，因為一開始已經全域擋過了
                    infraredRequestRepository.createMeasureRequestForContainer(c21, INFRARED_ID);
                    log.info("[GP2] Site#21 測高：container#{} → Infrared#{}", c21, INFRARED_ID);
                    return Optional.empty();
                }
            }
            // Site#21 有容器且已 verified → 本輪不需 GP2 動作
            //log.debug("[GP2] {} 有容器且已 verified，略過。", TARGET_NAME);
            return Optional.empty();
        }

        // 3) 夾爪空手 & Site#21 空 → 檢查 TR7@VIRTUAL#11，建 PICK
        Optional<Long> containerOnTR7 = locationTrackingRepository.findContainerOnTransfer(TRANSFER7_ID);
        if (containerOnTR7.isEmpty()) {
            //log.debug("[GP2] {} 無帳，略過 PICK", TRANSFER7_NAME);
            return Optional.empty();
        }

        TransferDeviceStatus ds7 = transferStatusCache.getLatest(TRANSFER7_NAME);
        boolean dsFresh = ds7 != null && ds7.isValidAndComplete(3);
        Integer level = safeGetLevel(ds7);
        boolean atV11 = dsFresh && level != null && level == VIRTUAL11_LEVEL;
        if (!atV11) {
            //log.debug("[GP2] {} 不在 VIRTUAL#11（level={} fresh={}），略過 PICK", TRANSFER7_NAME, level, dsFresh);
            return Optional.empty();
        }

        // PICK：VIRTUAL#11 → Site#21
        return createRequestByName(gripperId, "PICK", SOURCE_NAME_VISUAL, TARGET_NAME, containerOnTR7.get());
    }

    // ------- 建單（以名稱查點位） -------
    private Optional<Long> createRequestByName(Long gripperId, String taskType, String sourceName, String targetName, Long containerMainId) {
        Long sourceId = sourceName != null
                ? locationPointRepository.findByName(sourceName)
                .map(LocationPoint::getId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid source location name: " + sourceName))
                : null;

        Long targetId = locationPointRepository.findByName(targetName)
                .map(LocationPoint::getId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid target location name: " + targetName));

        return createRequestById(gripperId, taskType, sourceId, targetId, sourceName, targetName, containerMainId);
    }

    private Optional<Long> createRequestById(Long gripperId, String taskType,
                                             Long sourceId, Long targetId,
                                             String sourceName, String targetName,
                                             Long containerMainId) {

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

        // 目標高度：以 container_attr.tray_thickness_mm
        Double trayThickness = resolveTrayThicknessSafe(containerMainId);
        if (trayThickness == null) {
            log.warn("[GP2] 解析 tray_thickness_mm 失敗，跳過建單。containerId={}", containerMainId);
            return Optional.empty();
        }
        request.setTargetHeightMm(BigDecimal.valueOf(trayThickness));
        request.setLayerCount(0);

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[GP2] 建立 GripperRequest 成功: {} → {} [{}], containerId={}",
                    sourceName != null ? sourceName : ("Gripper#" + gripperId), targetName, taskType, containerMainId);
            return Optional.of(request.getId());
        } else {
            log.warn("[GP2] 建立 GripperRequest 失敗 [{}]", taskType);
            return Optional.empty();
        }
    }

    private GripperGenerationContext generationContext() {
        return new GripperGenerationContext(
                requestRepository,
                taskRepository,
                infraredRequestRepository,
                infraredTaskRepository,
                workingBeamRequestRepository,
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

    // ------- Site#21 測高輔助 -------
    private boolean hasVerifiedQuantity(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                .orElse(0) > 0;
    }

    // ------- 解析厚度屬性 -------
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

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try {
            return ds.getLevel();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
