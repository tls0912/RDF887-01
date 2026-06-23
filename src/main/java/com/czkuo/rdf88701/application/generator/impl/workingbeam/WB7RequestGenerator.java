package com.czkuo.rdf88701.application.generator.impl.workingbeam;

import com.czkuo.rdf88701.application.generator.WorkingBeamRequestGenerator;
import com.czkuo.rdf88701.application.service.process.DeviceProcessStateReader;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WB7RequestGenerator（單線版）
 * 流向：Site#21 → Site#22 → Transfer#8，但決策順序「先看 Site#22 再看 Site#21」：
 *   1) 若 Site#22 有帳 → 檢查 TR8 是否在 VIRTUAL#14（Level）&（建議）TR8 為空 → 才建單（來源=Site#22）
 *   2) 若 Site#22 無帳，且 Site#21 有帳 → 同樣檢查 TR8 條件 → 才建單（來源=Site#21）
 * 全程阻擋條件：
 *   - WB7 自己忙碌（有未完成請求/任務） → 不建單
 *   - GP#2 有「DROP→Site#21」未完成請求/任務 → 不建單
 */
@Slf4j
@Component("WB7")
@RequiredArgsConstructor
public class WB7RequestGenerator implements WorkingBeamRequestGenerator {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final TransferStatusCache statusCache;
    private final DeviceProcessStateReader stateReader;

    // 檢查 GP2 是否有「DROP→Site#21」未完成請求/任務
    private final GripperRequestRepository gripperRequestRepository;
    private final GripperTaskRepository gripperTaskRepository;
    private final LocationPointRepository locationPointRepository;

    // 需要查 verified_quantity
    private final ContainerDataRepository containerDataRepository;

    private final WorkingBeamStatusCache workingBeamStatusCache;

    private static final String SITE_21 = "Site#21";
    private static final String SITE_22 = "Site#22";

    private static final long   TRANSFER_8_ID   = 8L;
    private static final String TRANSFER_8_NAME = "Transfer#8";

    private static final long GRIPPER_2_ID   = 2L;

    private static final String VIRTUAL_14_NAME = "VIRTUAL#14";
    private static final int    LEVEL_V14       = 214;

    @Override
    public Optional<Long> generateRequest(Long workingBeamId) {
        if (!deviceIsRun(WorkingBeamGeneratorConstants.SPLIT_MERGE_AREA))
            return Optional.empty();
        // 0) WB7 忙碌 → 略過
        if (requestRepository.existsUnfinishedRequestForBeam(workingBeamId)
                || taskRepository.existsUnfinishedTaskForBeam(workingBeamId)) {
            //log.debug("[WB7] beam#{} 忙碌（未完成請求/任務），略過", workingBeamId);
            return Optional.empty();
        }

        String beamName = "WorkingBeam#" + workingBeamId;
        WorkingBeamDeviceStatus deviceStatus = workingBeamStatusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) {
            //log.debug("[WB7] WB7 狀態快取無效（ds==null 或 !isValidAndComplete(3)），此次不建請求");
            return Optional.empty();
        }

        if (!deviceStatus.isTransferStandby()) {
            //log.debug("[WB7] WB7 設備狀態尚未準備好，此次不建請求");
            return Optional.empty();
        }

        // 1) GP2 有「DROP→Site#21」未完成請求/任務 → 阻擋
        if (hasPendingGp2DropToSite21()) {
            //log.debug("[WB7] 阻擋：存在 GP#2 的未完成 DROP → {}，本次不建單", SITE_21);
            return Optional.empty();
        }



        // 2) 先看 Site#22，否則看 Site#21
        String sourceSite = null;
        boolean site22HasAccount = false;
        Optional<Long> site22Container = locationTrackingRepository.findContainerAtLocationName(SITE_22);
        Optional<Long> site21Container = locationTrackingRepository.findContainerAtLocationName(SITE_21);
        if (site22Container.isPresent()) {
            sourceSite = SITE_22;
            site22HasAccount = true;
            if (site21Container.isPresent()) {
                Long c21 = site21Container.get();
                if (!hasVerifiedQuantity(c21)) {
                    return Optional.empty();
                }
            }
        } else if (site21Container.isPresent()) {
            sourceSite = SITE_21;
        } else {
            //log.debug("[WB7] {} 與 {} 皆無帳，略過建立請求", SITE_22, SITE_21);
            return Optional.empty();
        }

        // 若來源是 Site#21，出手前必須已測高（verified_quantity > 0）
        if (SITE_21.equals(sourceSite)) {
            if (site21Container.isPresent()) {
                Long c21 = site21Container.get();
                if (!hasVerifiedQuantity(c21)) {
                    //log.debug("[WB7] {} 有帳但未測高（verified_quantity=0），禁止建立 WB7 任務", SITE_21); // NEW
                    return Optional.empty();
                }
            }
        }

        // 3) Site#22 有帳 → TR8 必須在 V14 且 TR8 為空
        if (site22HasAccount) {
            boolean tr8HasContainer = locationTrackingRepository.findContainerOnTransfer(TRANSFER_8_ID).isPresent();
            if (tr8HasContainer) {
                //log.debug("[WB7] 阻擋：{} 在 {} 但已有帳，避免衝突 → 不建單", TRANSFER_8_NAME, VIRTUAL_14_NAME);
                return Optional.empty();
            }

            TransferDeviceStatus ds = statusCache.getLatest(TRANSFER_8_NAME);
            boolean fresh = ds != null && ds.isValidAndComplete(3);
            Integer level = safeGetLevel(ds);
            boolean atV14 = fresh && level != null && level == LEVEL_V14;
            if (!atV14) {
                //log.debug("[WB7] {} 不在 {}（fresh={}, level={}），不建單", TRANSFER_8_NAME, VIRTUAL_14_NAME, fresh, level);
                return Optional.empty();
            }
        }

        // 4) 建立 WB7 請求（來源：Site#22 優先，其次 Site#21 → 目標 TR8）
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
            log.info("[WB7] 建立 WorkingBeamRequest 成功: beam#{} from {} → {} reqId={}, key={}",
                    workingBeamId, sourceSite, TRANSFER_8_NAME, request.getId(), request.getRequestKey());
            return Optional.of(request.getId());
        } else {
            log.warn("[WB7] 建立 WorkingBeamRequest 失敗: beam#{} from {}", workingBeamId, sourceSite);
            return Optional.empty();
        }
    }

    /** 檢查是否存在「GP#2 的 DROP → Site#21」未完成請求/任務。 */
    private boolean hasPendingGp2DropToSite21() {
        Long targetId = WorkingBeamLocationCache.findLocationId(locationPointRepository, SITE_21);
        if (targetId == null) {
            log.warn("[WB7] 找不到 {} 的點位 ID，保守視為 GP2-DROP 存在 → 阻擋建單", SITE_21);
            return true;
        }
        // 若你的 Repo 有精確查詢，請用它們；否則使用粗略阻擋做為備援
        try {
            boolean req = gripperRequestRepository
                    .existsUnfinishedRequestForDeviceToTargetAndType(GRIPPER_2_ID, targetId, "DROP");
            if (req) return true;
            boolean task = gripperTaskRepository
                    .existsUnfinishedTaskForGripperToTargetAndType(GRIPPER_2_ID, targetId, "DROP");
            return task;
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            boolean anyReq = gripperRequestRepository.existsUnfinishedRequestForDevice(GRIPPER_2_ID);
            if (anyReq) return true;
            boolean anyTask = gripperTaskRepository.existsUnfinishedTaskForGripper(GRIPPER_2_ID);
            return anyTask;
        }
    }

    // 測高判斷
    private boolean hasVerifiedQuantity(Long containerMainId) {
        return containerDataRepository.findByContainerMainId(containerMainId)
                .map(cd -> cd.getVerifiedQuantity() != null ? cd.getVerifiedQuantity() : 0)
                .orElse(0) > 0;
    }

    private Integer safeGetLevel(TransferDeviceStatus ds) {
        try { return ds.getLevel(); } catch (Throwable ignore) { return null; }
    }
    private boolean deviceIsRun(String deviceName) {
        return stateReader.getBestEffort(deviceName).getStatus() != ProcessStatus.STOP;
    }
}
