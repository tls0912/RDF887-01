package com.czkuo.rdf88701.application.service.transfer;

import com.czkuo.rdf88701.common.enums.CraneTaskStatus;
import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Crane 任務完成時，執行帳籍轉移作業（TO 段成功 → container_main 的帳籍異動）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraneTaskTransferService {

    private final LocationPointRepository locationPointRepository;
    private final LocationFlowRepository locationFlowRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final CraneTaskFollowUpRecordRepository craneTaskFollowUpRecordRepository;
    private final CraneTaskRepository craneTaskRepository;

    /**
     * FROM 成功 → 關閉來源 flow、帳籍轉到對應天車暫置點（Crane#{craneId}）
     */
    @Transactional
    public void markFlowExitOnFromSuccess(CraneTask task) {
        Long containerMainId = task.getContainerMainId();
        Long taskId = task.getId();
        Long sourceLocationId = task.getSourceLocationId();
        String craneId = task.getCraneId();
        Long craneLocId = resolveCraneStageLocationId(craneId);

        log.info("[Crane帳籍補帳][FROM] 成功 container#{} 任務#{} → 暫置點 Crane#{}", containerMainId, taskId, craneId);

        // 1) 關閉上一筆 flow（來源站點）
        int updated = locationFlowRepository.markPreviousAsLeft(containerMainId, LocalDateTime.now());
        if (updated > 0) {
            //log.debug("[Crane帳籍補帳][FROM] 關閉舊 flow {} 筆", updated);
        }

        // 2) 入帳到暫置點
        LocationFlow craneFlow = new LocationFlow();
        craneFlow.setContainerMainId(containerMainId);
        craneFlow.setLocationPointId(craneLocId);
        craneFlow.setEntryType("PLC");
        craneFlow.setArrivedTime(LocalDateTime.now());
        craneFlow.setSourceTaskId(taskId);
        locationFlowRepository.insert(craneFlow);

        // 3) 更新/建立 tracking → 暫置點
        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByContainerMainId(containerMainId);
        if (trackingOpt.isPresent()) {
            locationTrackingRepository.updateLocation(containerMainId, craneLocId, craneFlow.getId());
            //log.debug("[Crane帳籍補帳][FROM] 更新 tracking → Crane#{}(id={})", craneId, craneLocId);
        } else {
            LocationTracking tracking = new LocationTracking();
            tracking.setContainerMainId(containerMainId);
            tracking.setLocationPointId(craneLocId);
            tracking.setArrivedTime(LocalDateTime.now());
            tracking.setFlowId(craneFlow.getId());
            locationTrackingRepository.save(tracking);
            //log.debug("[Crane帳籍補帳][FROM] 建立 tracking → Crane#{}(id={})", craneId, craneLocId);
        }

        // 4) 釋放來源、佔用暫置點
        if (sourceLocationId != null && !sourceLocationId.equals(craneLocId)) {
            locationPointRepository.markVacant(sourceLocationId);
            //log.debug("[Crane帳籍補帳][FROM] 原位置釋放 location#{} → N", sourceLocationId);
        }
        locationPointRepository.markOccupied(craneLocId);
        //log.debug("[Crane帳籍補帳][FROM] 暫置點佔用 Crane#{}(id={}) → Y", craneId, craneLocId);
    }

    /**
     * TO 成功 → 關閉暫置點 flow、帳籍轉到目的地
     */
    @Transactional
    public void updateFlowAndTrackingOnToSuccess(CraneTask task) {
        Long containerMainId = task.getContainerMainId();
        Long targetLocationId = task.getTargetLocationId();
        Long taskId = task.getId();
        String craneId = task.getCraneId();
        Long craneLocId = resolveCraneStageLocationId(craneId);

        log.info("[Crane帳籍入帳][TO] 成功 container#{} → 位置#{} (任務#{})，自暫置點 Crane#{} 移轉",
                containerMainId, targetLocationId, taskId, craneId);

        // 0) 關閉上一筆 flow（預期為暫置點）
        int updated = locationFlowRepository.markPreviousAsLeft(containerMainId, LocalDateTime.now());
        if (updated > 0) {
            //log.debug("[Crane帳籍入帳][TO] 關閉 Crane#{} flow {} 筆", craneId, updated);
        }

        // 1) 入帳到目的地
        LocationFlow newFlow = new LocationFlow();
        newFlow.setContainerMainId(containerMainId);
        newFlow.setLocationPointId(targetLocationId);
        newFlow.setEntryType("PLC");
        newFlow.setArrivedTime(LocalDateTime.now());
        newFlow.setSourceTaskId(taskId);
        locationFlowRepository.insert(newFlow);

        // 2) 更新/建立 tracking → 目的地
        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByContainerMainId(containerMainId);
        if (trackingOpt.isPresent()) {
            locationTrackingRepository.updateLocation(containerMainId, targetLocationId, newFlow.getId());
            //log.debug("[Crane帳籍入帳][TO] 更新 tracking → 位置#{}", targetLocationId);
        } else {
            LocationTracking tracking = new LocationTracking();
            tracking.setContainerMainId(containerMainId);
            tracking.setLocationPointId(targetLocationId);
            tracking.setArrivedTime(LocalDateTime.now());
            tracking.setFlowId(newFlow.getId());
            locationTrackingRepository.save(tracking);
            //log.debug("[Crane帳籍入帳][TO] 建立 tracking → 位置#{}", targetLocationId);
        }

        // 3) 釋放暫置點、佔用目的地
        locationPointRepository.markVacant(craneLocId);
        //log.debug("[Crane帳籍入帳][TO] 暫置點釋放 Crane#{}(id={}) → N", craneId, craneLocId);

        locationPointRepository.markOccupied(targetLocationId);
        //log.debug("[Crane帳籍入帳][TO] 新位置佔用 location#{} → Y", targetLocationId);
    }

    @Transactional
    public void recordFollowUpRequiredFailure(CraneTask failedTask, String reasonCode, String reasonDesc) {
        Long taskId = failedTask.getId();
        Long containerId = failedTask.getContainerMainId();

        // 檢查這筆任務是否本身就是補償任務
        Optional<CraneTaskFollowUpRecord> recordOpt =
                craneTaskFollowUpRecordRepository.findByFollowUpTaskId(taskId);

        Long rootTaskId = recordOpt
                .map(CraneTaskFollowUpRecord::getRootTaskId)
                .orElse(taskId);  // 如果不是補償任務，就自己是 root

        CraneTaskFollowUpRecord record = new CraneTaskFollowUpRecord();
        record.setRootTaskId(rootTaskId);
        record.setOriginalTaskId(taskId);
        record.setReasonCode(reasonCode);
        record.setReasonDesc(reasonDesc);
        record.setFollowUpTaskId(null);
        record.setHandled(false);
        record.setHandledTime(null);

        craneTaskFollowUpRecordRepository.save(record);

        log.warn("[Crane補償登記] 任務#{} 放置失敗（container#{}），建立補償紀錄：reason={}({})",
                taskId, containerId, reasonCode, reasonDesc);
    }

    /**
     * 標記任務為完成
     */
    @Transactional
    public void markTaskCompleted(CraneTask task) {
        if (!CraneTaskStatus.COMPLETED.name().equals(task.getTaskStatus())) {
            task.setTaskStatus(CraneTaskStatus.COMPLETED.name());
            task.setCompletedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            craneTaskRepository.update(task);
            log.info("[Crane任務完成] 任務#{} 已標記為 COMPLETED", task.getId());
        }
    }

    /**
     * 標記任務為失敗
     */
    @Transactional
    public void markTaskFailed(CraneTask task, String reason) {
        if (!CraneTaskStatus.FAILED.name().equals(task.getTaskStatus())) {
            task.setTaskStatus(CraneTaskStatus.FAILED.name());
            task.setUpdatedTime(LocalDateTime.now());
            task.setCompletedTime(LocalDateTime.now());
            craneTaskRepository.update(task);
            log.error("[Crane任務失敗] 任務#{} 已標記為 FAILED，原因：{}", task.getId(), reason);
        }
    }

    /**
     * 根據 Crane ID 查找對應的 Crane Site 的 location_point.id
     * - 對應規則：location_point.name = 'Crane#<craneId>'
     */
    private Long resolveCraneStageLocationId(String craneId) {
        if (craneId == null) return null;
        String expectedName = "Crane#" + craneId;
        return TransferServiceLocationCache.findLocationId(locationPointRepository, expectedName).orElse(null);
    }
}
