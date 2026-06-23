package com.czkuo.rdf88701.application.service.transfer;

import com.czkuo.rdf88701.domain.repository.LocationFlowRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.domain.repository.LocationTrackingRepository;
import com.czkuo.rdf88701.domain.repository.TransferTaskRepository;
import com.czkuo.rdf88701.infra.entity.LocationFlow;
import com.czkuo.rdf88701.infra.entity.LocationTracking;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Transfer 任務完成時的帳籍處理服務
 * <p>
 * - 根據任務類型決定是否執行帳籍轉移
 * - MOVE  → 無需異動帳籍（只移動於 Transfer 上）
 * - PICK  → 標記原位置 flow 為離開，並將帳籍移入 Transfer 對應站點
 * - DROP  → 建立目標位置 flow 與 tracking，並更新占用狀態
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferTaskTransferService {

    private final LocationFlowRepository locationFlowRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationPointRepository locationPointRepository;
    private final TransferTaskRepository transferTaskRepository;

    /**
     * 任務成功後根據類型執行對應帳籍異動
     */
    @Transactional
    public void updateFlowAndTrackingOnSuccess(TransferTask task) {
        String taskType = task.getTaskType(); // MOVE / PICK / DROP
        Long containerId = task.getContainerMainId();
        Long taskId = task.getId();

        log.info("[Transfer帳籍處理] 任務#{} 類型={}, container#{}", taskId, taskType, containerId);

        switch (taskType) {
            case "MOVE" -> {
                // MOVE：Transfer 內部移動，帳籍不變
                //log.debug("[Transfer帳籍] MOVE 類型，無帳籍異動");
            }
            case "PICK" -> {
                // PICK：從來源取出 → 標記離開舊位置，並帳籍移入 Transfer 對應站點
                markPreviousAsLeft(containerId, taskId);

                Long transferSiteId = resolveTransferLocationId(task.getTransferId());
                if (transferSiteId != null) {
                    markArrived(containerId, transferSiteId, taskId);
                } else {
                    log.error("[Transfer帳籍] 找不到 Transfer 裝置對應的位置點名稱: Transfer#{}", task.getTransferId());
                }
            }
            case "DROP" -> {
                // DROP：從 Transfer 搬出 → 標記離開 Transfer，並入帳目標位置
                markPreviousAsLeft(containerId, taskId);
                Long toLocationId = task.getToLocationId();
                markArrived(containerId, toLocationId, taskId);
            }
            default -> {
                log.warn("[Transfer帳籍處理] 任務#{} 類型未知：{}", taskId, taskType);
            }
        }
    }

    /**
     * 標記 container 離開原位置（通常用於 PICK 任務）
     */
    private void markPreviousAsLeft(Long containerId, Long taskId) {
        log.info("[Transfer帳籍] 標記 container#{} 離開 (任務#{})", containerId, taskId);
        int updated = locationFlowRepository.markPreviousAsLeft(containerId, LocalDateTime.now());
        if (updated > 0) {
            //log.debug("[Transfer帳籍] 成功標記離開，共 {} 筆 flow", updated);
        }

        // 清除原位置的 is_occupied 狀態
        locationTrackingRepository.findByContainerMainId(containerId).ifPresent(tracking -> {
            Long prevLocationId = tracking.getLocationPointId();
            locationPointRepository.markVacant(prevLocationId);
            //log.debug("[Transfer帳籍] 原位置#{} 標記為未占用", prevLocationId);
        });
    }

    /**
     * 入帳處理：建立 flow 與 tracking，標記位置占用
     */
    private void markArrived(Long containerId, Long locationId, Long taskId) {
        log.info("[Transfer帳籍] 入帳 container#{} → 位置#{} (任務#{})", containerId, locationId, taskId);

        // 建立入帳 flow
        LocationFlow flow = new LocationFlow();
        flow.setContainerMainId(containerId);
        flow.setLocationPointId(locationId);
        flow.setArrivedTime(LocalDateTime.now());
        flow.setEntryType("PLC");
        flow.setSourceTaskId(taskId);
        locationFlowRepository.insert(flow);

        // 更新或建立 tracking
        Optional<LocationTracking> trackingOpt = locationTrackingRepository.findByContainerMainId(containerId);
        if (trackingOpt.isPresent()) {
            locationTrackingRepository.updateLocation(containerId, locationId, flow.getId());
        } else {
            LocationTracking tracking = new LocationTracking();
            tracking.setContainerMainId(containerId);
            tracking.setLocationPointId(locationId);
            tracking.setArrivedTime(LocalDateTime.now());
            tracking.setFlowId(flow.getId());
            locationTrackingRepository.save(tracking);
        }

        // 標記目標位置占用
        locationPointRepository.markOccupied(locationId);
        //log.debug("[Transfer帳籍] 目標位置#{} 標記為占用", locationId);
    }

    /**
     * 根據 Transfer ID 查找對應的 Transfer Site 的 location_point.id
     * - 對應規則：location_point.name = 'Transfer#<transferId>'
     */
    private Long resolveTransferLocationId(Long transferId) {
        if (transferId == null) return null;
        String expectedName = "Transfer#" + transferId;
        return TransferServiceLocationCache.findLocationId(locationPointRepository, expectedName).orElse(null);
    }

    /**
     * 任務完成標記
     */
    @Transactional
    public void markTaskCompleted(TransferTask task) {
        if (!"COMPLETED".equals(task.getTaskStatus())) {
            task.setTaskStatus("COMPLETED");
            task.setCompletedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            transferTaskRepository.update(task);
            log.info("[Transfer任務完成] 任務#{} 已標記為 COMPLETED", task.getId());
        }
    }

    /**
     * 任務失敗標記
     */
    @Transactional
    public void markTaskFailed(TransferTask task, String reason) {
        if (!"FAILED".equals(task.getTaskStatus())) {
            task.setTaskStatus("FAILED");
            task.setCompletedTime(LocalDateTime.now());
            task.setUpdatedTime(LocalDateTime.now());
            transferTaskRepository.update(task);
            log.error("[Transfer任務失敗] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
        }
    }
}

