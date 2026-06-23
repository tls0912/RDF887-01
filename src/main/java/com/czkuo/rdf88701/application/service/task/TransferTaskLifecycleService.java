package com.czkuo.rdf88701.application.service.task;

import com.czkuo.rdf88701.common.enums.TransferTaskStatus;
import com.czkuo.rdf88701.domain.repository.TransferTaskRepository;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 統一管理 Transfer 任務狀態變更（狀態更新集中處理）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferTaskLifecycleService {

    private final TransferTaskRepository transferTaskRepository;

    @Transactional
    public void updateStatus(TransferTask task, TransferTaskStatus newStatus) {
        if (!newStatus.name().equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus(newStatus.name());
            task.setUpdatedTime(now);

            if (newStatus == TransferTaskStatus.DISPATCHED) {
                task.setDispatchedTime(now);
            } else if (newStatus == TransferTaskStatus.COMPLETED || newStatus == TransferTaskStatus.FAILED) {
                task.setCompletedTime(now);
            } else if (newStatus == TransferTaskStatus.CANCELLED) {
                task.setCancelledTime(now);
            }

            transferTaskRepository.update(task);
            log.info("[TransferTask] 任務#{} 狀態更新為 {}", task.getId(), newStatus.name());
        }
    }

    @Transactional
    public void markDispatched(TransferTask task) {
        updateStatus(task, TransferTaskStatus.DISPATCHED);
    }

    @Transactional
    public void markInProgress(TransferTask task) {
        updateStatus(task, TransferTaskStatus.IN_PROGRESS);
    }

    @Transactional
    public void markRetry(TransferTask task) {
        updateStatus(task, TransferTaskStatus.RETRY);
    }

    @Transactional
    public void markCompleted(TransferTask task) {
        updateStatus(task, TransferTaskStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(TransferTask task) {
        updateStatus(task, TransferTaskStatus.FAILED);
    }

    @Transactional
    public void markFailed(TransferTask task, String reason) {
        markFailed(task);
        log.error("[TransferTask] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
    }

    @Transactional
    public boolean markTaskAsDone(TransferTask task) {
        return transferTaskRepository.markTaskAsDone(task.getId());
    }
}
