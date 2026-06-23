package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.service.command.CraneRequestCommandService;
import com.czkuo.rdf88701.config.plc.PlcCraneRegistry;
import com.czkuo.rdf88701.domain.repository.CraneRequestRepository;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 真正處理 Crane Request Monitor 的 Service
 * → 被 Scheduler 呼叫，擁有 Transactional
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CraneRequestMonitorService {

    private final CraneRequestRepository craneRequestRepository;
    private final CraneRequestCommandService craneRequestCommandService;
    private final PlcCraneRegistry plcCraneRegistry;

    /**
     * 主掃描邏輯
     * 必須在 Service 層，才能讓 Spring AOP 加上 transaction proxy
     */
    @Transactional
    public void monitorUnacceptedRequests() {
        List<CraneRequest> pendingRequests = craneRequestRepository.findUnacceptedRequests();
        if (pendingRequests.isEmpty()) {
            return;
        }

        //log.debug("[Monitor] 掃描到 {} 筆未處理 crane_request", pendingRequests.size());

        for (CraneRequest request : pendingRequests) {
            try {
                String assignedCrane = assignCrane(request);
                craneRequestCommandService.convertRequestToTask(request.getId(), assignedCrane);
                log.info("[Monitor] 已轉換 CraneRequest id={} → Task，crane={}", request.getId(), assignedCrane);
            } catch (Exception e) {
                log.warn("[Monitor] 處理 CraneRequest id={} 失敗: {}", request.getId(), e.getMessage());
            }
        }
    }

    /**
     * 指派對應的 Crane
     * 現階段取第一台，後續可根據 request 條件做智慧選擇
     */
    private String assignCrane(CraneRequest request) {
        int craneId = plcCraneRegistry.getCranes().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("無可用 Crane 設定"))
                .getId();   // PLC domain 還是 int

        return String.valueOf(craneId);
    }
}
