package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Crane 任務查詢服務
 *
 * 提供應用層查詢介面，封裝對 Repository 的操作
 * Application 與 Monitor/Controller 等類別透過本 Service 查詢任務
 */
@Service
@RequiredArgsConstructor
public class CraneTaskQueryService {

    private final CraneTaskRepository craneTaskRepository;

    /**
     * 查詢所有未完成任務（PENDING 或 DISPATCHED）
     */
    public List<CraneTask> queryPendingOrDispatchedTasks() {
        return craneTaskRepository.findPendingOrDispatchedTasks();
    }

    /**
     * 查詢單一 crane 下，優先處理的任務（DISPATCHED > PENDING，priority DESC）
     */
    public Optional<CraneTask> findTopPriorityTaskByCrane(int craneId) {
        return craneTaskRepository.findTopTaskByCraneOrdered(craneId);
    }
}