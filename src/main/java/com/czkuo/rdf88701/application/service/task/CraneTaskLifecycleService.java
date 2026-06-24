package com.czkuo.rdf88701.application.service.task;

import com.czkuo.rdf88701.common.enums.CraneTaskStatus;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 統一管理 Crane 任務狀態變更（狀態更新集中處理）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraneTaskLifecycleService {

    private final CraneTaskRepository craneTaskRepository;

    @Transactional
    public void updateStatus(CraneTask task, CraneTaskStatus newStatus) {
        if (!newStatus.name().equals(task.getTaskStatus())) {
            LocalDateTime now = LocalDateTime.now();
            task.setTaskStatus(newStatus.name());
            task.setUpdatedTime(now);

            if (newStatus == CraneTaskStatus.DISPATCHED) {
                task.setDispatchedTime(now);
            }
            else if (newStatus == CraneTaskStatus.COMPLETED || newStatus == CraneTaskStatus.FAILED) {
                task.setCompletedTime(now);
            }

            craneTaskRepository.update(task);
            log.info("[Task狀態更新] 任務#{} 狀態 → {}", task.getId(), newStatus.name());
        }
    }

    @Transactional
    public void markDispatched(CraneTask task) {
        updateStatus(task, CraneTaskStatus.DISPATCHED);
    }

    @Transactional
    public void markInProgress(CraneTask task) {
        updateStatus(task, CraneTaskStatus.IN_PROGRESS);
    }

    @Transactional
    public void markRetry(CraneTask task) {
        updateStatus(task, CraneTaskStatus.RETRY);
    }

    @Transactional
    public void markCompleted(CraneTask task) {
        updateStatus(task, CraneTaskStatus.COMPLETED);
    }

    @Transactional
    public void markFailed(CraneTask task) {
        updateStatus(task, CraneTaskStatus.FAILED);
    }

    @Transactional
    public void markFailed(CraneTask task, String reason) {
        markFailed(task);
        log.error("[Task失敗] 任務#{} 已標記為 FAILED，原因：{}", task.getId(), reason);
    }

    @Transactional
    public boolean markTaskAsDone(CraneTask task) {
        return craneTaskRepository.markTaskAsDone(task.getId());
    }
}
