package com.czkuo.rdf88701.application.service.task;

import com.czkuo.rdf88701.common.enums.GripperTaskStatus;
import com.czkuo.rdf88701.domain.repository.GripperTaskRepository;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 統一管理 Gripper 任務狀態變更（狀態更新集中處理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GripperTaskLifecycleService {

    private final GripperTaskRepository gripperTaskRepository;

    /**
     * 更新 Gripper 任務狀態，並自動記錄對應時間欄位
     */
    @Transactional
    public void updateStatus(GripperTask task, GripperTaskStatus newStatus) {
        if (!newStatus.name().equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus(newStatus.name());
            task.setUpdatedTime(now);

            if (newStatus == GripperTaskStatus.DISPATCHED) {
                task.setDispatchedTime(now);
            } else if (newStatus == GripperTaskStatus.COMPLETED || newStatus == GripperTaskStatus.FAILED) {
                task.setCompletedTime(now);
            } else if (newStatus == GripperTaskStatus.CANCELLED) {
                task.setCancelledTime(now);
            }

            gripperTaskRepository.update(task);
            log.info("[GripperTask] 任務#{} 狀態更新為 {}", task.getId(), newStatus.name());
        }
    }

    @Transactional
    public void markDispatched(GripperTask task) {
        updateStatus(task, GripperTaskStatus.DISPATCHED);
    }

    @Transactional
    public void markInProgress(GripperTask task) {
        updateStatus(task, GripperTaskStatus.IN_PROGRESS);
    }

    @Transactional
    public void markRetry(GripperTask task) {
        updateStatus(task, GripperTaskStatus.RETRY);
    }

    @Transactional
    public void markCompleted(GripperTask task) {
        updateStatus(task, GripperTaskStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(GripperTask task) {
        updateStatus(task, GripperTaskStatus.FAILED);
    }

    @Transactional
    public void markFailed(GripperTask task, String reason) {
        markFailed(task);
        log.error("[GripperTask] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
    }

    /**
     * 標記任務已結束（補寫 done_time），適用於已完成任務結尾處理
     */
    @Transactional
    public boolean markTaskAsDone(GripperTask task) {
        return gripperTaskRepository.markTaskAsDone(task.getId());
    }
}