package com.czkuo.rdf88701.application.service.query;

import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Infrared 任務查詢服務
 * <p>
 * 提供應用層查詢介面，封裝對 Repository 的操作
 * Application 與 Monitor/Controller 等類別透過本 Service 查詢任務
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class InfraredTaskQueryService {

    private final InfraredTaskRepository infraredTaskRepository;

    /**
     * 查詢單一 Infrared 下，優先處理的任務（DISPATCHED > PENDING，priority DESC）
     */
    public Optional<InfraredTask> findTopPriorityTaskByInfrared(int infraredId) {
        return infraredTaskRepository.findTopTaskByInfraredOrdered(infraredId);
    }
}
