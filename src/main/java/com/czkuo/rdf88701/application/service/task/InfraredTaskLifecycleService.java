package com.czkuo.rdf88701.application.service.task;

import com.czkuo.rdf88701.common.enums.InfraredTaskStatus;
import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 統一管理 Infrared 任務狀態變更（狀態更新集中處理）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfraredTaskLifecycleService {

    private final InfraredTaskRepository infraredTaskRepository;

    @Transactional
    public void updateStatus(InfraredTask task, InfraredTaskStatus newStatus) {
        if (!newStatus.name().equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus(newStatus.name());
            task.setUpdatedTime(now);

            if (newStatus == InfraredTaskStatus.DISPATCHED) {
                task.setDispatchedTime(now);
            }
            else if (newStatus == InfraredTaskStatus.COMPLETED || newStatus == InfraredTaskStatus.FAILED) {
                task.setCompletedTime(now);
            }
            else if (newStatus == InfraredTaskStatus.CANCELLED) {
                task.setCancelledTime(now);
            }

            infraredTaskRepository.update(task);
            log.info("[InfraredTask] 任務#{} 狀態更新為 {}", task.getId(), newStatus.name());
        }
    }

    @Transactional
    public void markDispatched(InfraredTask task) {
        updateStatus(task, InfraredTaskStatus.DISPATCHED);
    }

    @Transactional
    public void markInProgress(InfraredTask task) {
        updateStatus(task, InfraredTaskStatus.IN_PROGRESS);
    }

    @Transactional
    public void markRetry(InfraredTask task) {
        updateStatus(task, InfraredTaskStatus.RETRY);
    }

    @Transactional
    public void markCompleted(InfraredTask task) {
        updateStatus(task, InfraredTaskStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(InfraredTask task) {
        updateStatus(task, InfraredTaskStatus.FAILED);
    }

    @Transactional
    public void markFailed(InfraredTask task, String reason) {
        markFailed(task);
        log.error("[InfraredTask] 任務#{} 標記為 FAILED，原因：{}", task.getId(), reason);
    }

    @Transactional
    public boolean markTaskAsDone(InfraredTask task) {
        return infraredTaskRepository.markTaskAsDone(task.getId());
    }
}
