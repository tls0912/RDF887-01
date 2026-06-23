package com.czkuo.rdf88701.application.service.transfer;

import com.czkuo.rdf88701.domain.repository.*;
import com.czkuo.rdf88701.infra.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkingBeamTaskTransferService {

    private final WorkingBeamControlRangeRepository controlRangeRepository;
    private final LocationTrackingRepository locationTrackingRepository;
    private final LocationFlowRepository locationFlowRepository;
    private final LocationPointRepository locationPointRepository;
    private final WorkingBeamTaskRepository workingBeamTaskRepository;


    @Transactional
    public void updateFlowAndTrackingOnSuccess(WorkingBeamTask task) {
        Long beamId = Long.valueOf(task.getWorkingBeamId());
        Long taskId = task.getId();

        List<WorkingBeamControlRange> ranges = controlRangeRepository
                .findByWorkingBeamIdOrderByPositionOrder(beamId);

        Map<Long, LocationTracking> trackingMap =
                locationTrackingRepository.findContainersByWorkingBeamId(beamId)
                        .stream()
                        .collect(Collectors.toMap(
                                LocationTracking::getLocationPointId,
                                x -> x,
                                (a, b) -> {
                                    log.warn("duplicate tracking locationPointId={}", a.getLocationPointId());
                                    return a;
                                }
                        ));
        if (ranges.size() < 2) {
            log.warn("[WB帳籍] working_beam#{} 控制範圍不足，無法執行轉移", beamId);
            return;
        }

        // 倒序處理（從尾到頭，避免覆蓋）
        for (int i = ranges.size() - 2; i >= 0; i--) {

            Long locationPointId = ranges.get(i).getLocationPointId();
            // 檢查來源點是否有 tracking
            LocationTracking tracking = trackingMap.get(locationPointId);
            if (tracking == null) {
                continue;
            }
            moveContainer(ranges, i, taskId, tracking);
        }
    }

    public void moveContainer(List<WorkingBeamControlRange> ranges, int i, Long taskId, LocationTracking tracking) {

        Long fromLocId = ranges.get(i).getLocationPointId();
        Long toLocId = ranges.get(i + 1).getLocationPointId();
        LocalDateTime now = LocalDateTime.now();

        if (tracking == null) {
            log.warn("[WB帳籍] #{} 此位置無 container，跳過 (外層沒跳過，進到內層才跳)", fromLocId, fromLocId);
            return; // 此位置無 container，跳過
        }

        Long containerId = tracking.getContainerMainId();

        // 1. 標記舊 flow 離開
        int updated = locationFlowRepository.markPreviousAsLeft(containerId, now);
        if (updated > 0) {
            log.info("[WB帳籍] container#{} 離開位置#{}", containerId, fromLocId);
        }

        // 2. 建立新 flow
        LocationFlow flow = new LocationFlow();
        flow.setContainerMainId(containerId);
        flow.setLocationPointId(toLocId);
        flow.setArrivedTime(now);
        flow.setEntryType("PLC");
        flow.setSourceTaskId(taskId);
        locationFlowRepository.insert(flow);

        // 3. 更新 tracking
        locationTrackingRepository.updateLocation(containerId, toLocId, flow.getId());

        // 4. 更新位置佔用狀態
        locationPointRepository.markVacant(fromLocId);
        locationPointRepository.markOccupied(toLocId);

        log.info("[WB帳籍] container#{} 從位置#{} → 位置#{}", containerId, fromLocId, toLocId);

    }

    @Transactional
    public void markTaskCompleted(WorkingBeamTask task) {
        if (!"COMPLETED".equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus("COMPLETED");
            task.setCompletedTime(now);
            task.setUpdatedTime(now);
            workingBeamTaskRepository.update(task);
            log.info("[WB任務完成] 任務#{} 標記為 COMPLETED", task.getId());
        }
    }

    @Transactional
    public void markTaskFailed(WorkingBeamTask task, String reason) {
        if (!"FAILED".equals(task.getTaskStatus())) {
            task.setTaskStatus("FAILED");
            task.setUpdatedTime(LocalDateTime.now());
            workingBeamTaskRepository.update(task);
            log.error("[WB任務失敗] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
        }
    }
}
