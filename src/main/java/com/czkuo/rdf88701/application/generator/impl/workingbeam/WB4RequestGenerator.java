package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.GripperRequestRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * WB4RequestGenerator
 * - 當 Site#34 有容器，且 Site#35 無容器時，建立 WorkingBeam 請求
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component("WB4")
@RequiredArgsConstructor
public class WB4RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final WorkingBeamStatusCache workingBeamStatusCache;
    private final GripperRequestRepository gripperRepository;

    private static final String SOURCE_NAME = "Site#34";
    private static final String TARGET_NAME = "Site#35";
    private static final String BLOCK_SITE_NAME = "Gripper#5";
    private static final long BLOCK_GRIPPER_ID = 5;
    private final DeviceProcessStateReader stateReader;

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        // === 檢查是否已有未完成請求或任務 ===
        if (requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId)) {
            //log.debug("[WB4] WorkingBeam#{} 已有未完成請求或任務，略過", workingBeamId);
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB4] WB4 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB4] WB4 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // === Site#34 必須有帳，Site#35 Site#36 Gripper#5 必須為空 Gripper#5 必須閒置===
        Optional<String> blockedReason = findBlockReason();
        if (blockedReason.isPresent()) {
            log.debug("[WB4] {}", blockedReason.get());
            return Optional.empty();
        }

        // === 建立 WorkingBeamRequest 請求 ===
        WorkingBeamRequest request = new WorkingBeamRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setWorkingBeamId(workingBeamId);
        request.setDirection("IN");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());

        boolean saved = requestRepository.save(request);
        if (saved) {
            log.info("[WB4] 建立 WorkingBeamRequest 成功, ID={}, Key={}", request.getId(), request.getRequestKey());
            return Optional.of(request.getId());
        } else {
            log.warn("[WB4] 建立 WorkingBeamRequest 失敗");
            return Optional.empty();
        }
    }

    private Optional<String> findBlockReason() {
        boolean sourceHas = locationTrackingRepository.hasContainerAtLocationName(SOURCE_NAME);
        boolean targetHas = locationTrackingRepository.hasContainerAtLocationName(TARGET_NAME);
        boolean blockSiteHas = locationTrackingRepository.hasContainerAtLocationName(BLOCK_SITE_NAME);

        if (!sourceHas) {
            return Optional.of(SOURCE_NAME + " 無容器");
        }
        if (targetHas) {
            return Optional.of(TARGET_NAME + " 已有容器");
        }
        if (gripperRepository.existsUnfinishedRequestForDevice(BLOCK_GRIPPER_ID)) {
            return Optional.of("Gripper#" + BLOCK_GRIPPER_ID + " 忙碌中");
        }
        if (blockSiteHas) {
            return Optional.of(BLOCK_SITE_NAME + " 已有容器");
        }
        return Optional.empty();
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
