package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.service.command.WorkingBeamRequestCommandService;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WorkingBeam Request 處理服務
 * - 依照每台 WorkingBeam 分組處理 Request
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingBeamRequestMonitorService {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamRequestCommandService commandService;

    /**
     * 掃描未處理的 Request，依據 WorkingBeam 分組處理（支援擴展獨立策略）
     */
    @Transactional
    public void monitorUnacceptedRequestsByDevice() {
        List<WorkingBeamRequest> pendingRequests = requestRepository.findUnacceptedRequests();
        if (pendingRequests.isEmpty()) {
            return;
        }

        // 分組：每台 WorkingBeam 各自處理
        Map<Long, List<WorkingBeamRequest>> groupedByBeamId =
                pendingRequests.stream().collect(Collectors.groupingBy(WorkingBeamRequest::getWorkingBeamId));

        for (Map.Entry<Long, List<WorkingBeamRequest>> entry : groupedByBeamId.entrySet()) {
            Long beamId = entry.getKey();
            List<WorkingBeamRequest> requests = entry.getValue();

            WorkingBeamRequest firstRequest = requests.get(0); // 僅取第一筆

            try {
                commandService.convertRequestToTask(firstRequest.getId(), beamId);
                log.info("[Monitor] 已轉換 WorkingBeamRequest id={} → Task", firstRequest.getId());
            } catch (Exception e) {
                log.warn("[Monitor] 處理 WorkingBeamRequest id={} 失敗：{}", firstRequest.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 僅處理該設備第一筆未處理的 Request（若有）
     * @return 是否成功處理 1 筆
     */
    @Transactional
    public boolean monitorUnacceptedRequestsByDevice(String workingBeamId) {
        return requestRepository.findFirstUnacceptedByWorkingBeamName(workingBeamId)
                .map(request -> {
                    try {
                        commandService.convertRequestToTask(request.getId(), request.getWorkingBeamId());
                        return true;
                    } catch (Exception e) {
                        log.warn("[Monitor] 處理 WorkingBeamRequest id={} 失敗：{}", request.getId(), e.getMessage(), e);
                        return false;
                    }
                })
                .orElse(false);
    }
}
