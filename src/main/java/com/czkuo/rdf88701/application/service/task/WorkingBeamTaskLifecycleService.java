package com.czkuo.rdf88701.application.service.task;

import com.czkuo.rdf88701.common.enums.WorkingBeamTaskStatus;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 統一管理 WorkingBeam 任務狀態變更（狀態更新集中處理）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingBeamTaskLifecycleService {

    private final WorkingBeamTaskRepository workingBeamTaskRepository;

    @Transactional
    public void updateStatus(WorkingBeamTask task, WorkingBeamTaskStatus newStatus) {
        if (!newStatus.name().equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus(newStatus.name());
            task.setUpdatedTime(now);

            if (newStatus == WorkingBeamTaskStatus.DISPATCHED) {
                task.setDispatchedTime(now);
            }
            else if (newStatus == WorkingBeamTaskStatus.COMPLETED || newStatus == WorkingBeamTaskStatus.FAILED) {
                task.setCompletedTime(now);
            }
            else if (newStatus == WorkingBeamTaskStatus.CANCELLED) {
                task.setCancelledTime(now);
            }

            workingBeamTaskRepository.update(task);
            log.info("[WorkingBeamTask] 任務#{} 狀態更新為 {}", task.getId(), newStatus.name());
        }
    }

    @Transactional
    public void markDispatched(WorkingBeamTask task) {
        updateStatus(task, WorkingBeamTaskStatus.DISPATCHED);
    }

    @Transactional
    public void markInProgress(WorkingBeamTask task) {
        updateStatus(task, WorkingBeamTaskStatus.IN_PROGRESS);
    }

    @Transactional
    public void markRetry(WorkingBeamTask task) {
        updateStatus(task, WorkingBeamTaskStatus.RETRY);
    }

    @Transactional
    public void markCompleted(WorkingBeamTask task) {
        updateStatus(task, WorkingBeamTaskStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(WorkingBeamTask task) {
        updateStatus(task, WorkingBeamTaskStatus.FAILED);
    }

    @Transactional
    public void markFailed(WorkingBeamTask task, String reason) {
        markFailed(task);
        log.error("[WorkingBeamTask] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
    }

    @Transactional
    public boolean markTaskAsDone(WorkingBeamTask task) {
        return workingBeamTaskRepository.markTaskAsDone(task.getId());
    }
}
