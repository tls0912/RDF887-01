package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.generator.impl.gripper.GP4RequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.ContainerData;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * WB3RequestGenerator
 * - 當 Site#23 有容器，且 Site#24 無容器時，建立 WorkingBeam 請求
 */
@Slf4j
@Component("WB3")
@RequiredArgsConstructor
public class WB3RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final WorkingBeamStatusCache workingBeamStatusCache;
    private final GripperRequestRepository gripperRepository;
    private final ContainerDataRepository containerDataRepository;

    private static final String SOURCE_NAME = "Site#23";
    private static final String TARGET_NAME = "Site#24";
    private static final String BLOCK_SITE_NAME = "Gripper#4";
    private static final long BLOCK_GRIPPER_ID = 4;
    private static final String KIND_UNKNOWN           = "UNKNOWN";
    private static final String KIND_NORMAL_WITH_COVER = "NORMAL_WITH_COVER";
    private static final String KIND_NORMAL_NO_COVER   = "NORMAL_NO_COVER";
    private static final String KIND_ALL_COVER         = "ALL_COVER";
    private static final String KIND_EMPTY             = "EMPTY";
    private final DeviceProcessStateReader stateReader;

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        // === 檢查是否已有未完成請求或任務 ===
        if (requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId)) {
            //log.debug("[WB3] WorkingBeam#{} 已有未完成請求或任務，略過", workingBeamId);
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB3] WB3 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB3] WB3 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // === Site#23 必須有帳，Site#24 Gripper#4 必須為空 Gripper#4 必須閒置===
        Optional<String> blockedReason = findBlockReason();
        if (blockedReason.isPresent()) {
            log.debug("[WB3] {}", blockedReason.get());
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
            log.info("[WB3] 建立 WorkingBeamRequest 成功, ID={}, Key={}", request.getId(), request.getRequestKey());
            return Optional.of(request.getId());
        } else {
            log.warn("[WB3] 建立 WorkingBeamRequest 失敗");
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

    /**
     * 取得某容器的三路層數（工蓋/上蓋/一般）。
     * 欄位為 NULL 時，改以 content_kind + estimatedQuantity 做保守推導：
     * - ALL_COVER         → 工蓋=0，上蓋=estimated，一般=0
     * - NORMAL_NO_COVER   → 工蓋=0，上蓋=0，一般=estimated
     * - EMPTY             → 0,0,0
     * - 其他(UNKNOWN/NORMAL_WITH_COVER/NULL)
     *   → 工蓋=0，上蓋=(estimated>0 ? 1 : 0)，一般=max(estimated-上蓋,0)
     */
    private Counts countsAt(Long containerMainId) {
        if (containerMainId == null) return new Counts(0, 0, 0);
        ContainerData cd = containerDataRepository.findByContainerMainId(containerMainId).orElse(null);
        if (cd == null) return new Counts(0, 0, 0);

        Integer w = cd.getWorkCoverLayers();
        Integer c = cd.getCoverLayers();
        Integer p = cd.getProductLayers();

        if (w == null || c == null || p == null) {
            int estimated = cd.getEstimatedQuantity() == null ? 0 : cd.getEstimatedQuantity();
            String kind = cd.getContentKind();

            if (KIND_ALL_COVER.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = estimated;
                if (p == null) p = 0;
            } else if (KIND_NORMAL_NO_COVER.equals(kind) || KIND_EMPTY.equals(kind)) {
                if (w == null) w = 0;
                if (c == null) c = 0;
                if (p == null) p = estimated;
            } else {
                int cover = (estimated > 0 ? 1 : 0);
                if (w == null) w = 0;
                if (c == null) c = cover;
                if (p == null) p = Math.max(estimated - cover, 0);
            }
        }

        int wi = Math.max(0, w == null ? 0 : w);
        int ci = Math.max(0, c == null ? 0 : c);
        int pi = Math.max(0, p == null ? 0 : p);
        return new Counts(wi, ci, pi);
    }

    /** 三路層數封裝：工蓋 / 上蓋 / 一般 */
    private record Counts(int workCover, int cover, int product) {
        int covers() { return workCover + cover; }
        int total()  { return workCover + cover + product; }
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
