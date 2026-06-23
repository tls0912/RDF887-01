package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * WorkingBeam 任務查詢服務
 *
 * 提供應用層查詢介面，封裝對 Repository 的操作
 * Application 與 Monitor/Controller 等類別透過本 Service 查詢任務
 */
@Service
@RequiredArgsConstructor
public class WorkingBeamTaskQueryService {

    private final WorkingBeamTaskRepository workingBeamTaskRepository;

    /**
     * 查詢單一 WorkingBeam 下，優先處理的任務（DISPATCHED > PENDING，priority DESC）
     */
    public Optional<WorkingBeamTask> findTopPriorityTaskByWorkingBeam(int workingBeamId) {
        return workingBeamTaskRepository.findTopTaskByWorkingBeamOrdered(workingBeamId);
    }
}
