package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.service.command.InfraredRequestCommandService;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Infrared Request 處理服務
 * - 依照每台 Infrared 分組處理 Request
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfraredRequestMonitorService {

    private final InfraredRequestRepository requestRepository;
    private final InfraredRequestCommandService commandService;

    /**
     * 掃描未處理的 Request，依據 Infrared 分組處理（支援擴展獨立策略）
     */
    @Transactional
    public void monitorUnacceptedRequestsByDevice() {
        List<InfraredRequest> pendingRequests = requestRepository.findUnacceptedRequests();
        if (pendingRequests.isEmpty()) {
            return;
        }

        // 分組：每台 Infrared 各自處理
        Map<Long, List<InfraredRequest>> groupedByInfraredId =
                pendingRequests.stream().collect(Collectors.groupingBy(InfraredRequest::getInfraredId));

        for (Map.Entry<Long, List<InfraredRequest>> entry : groupedByInfraredId.entrySet()) {
            Long infraredId = entry.getKey();
            List<InfraredRequest> requests = entry.getValue();

            InfraredRequest firstRequest = requests.get(0); // 僅取第一筆

            try {
                commandService.convertRequestToTask(firstRequest.getId(), infraredId);
                log.info("[Monitor] 已轉換 InfraredRequest id={} → Task", firstRequest.getId());
            } catch (Exception e) {
                log.warn("[Monitor] 處理 InfraredRequest id={} 失敗：{}", firstRequest.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 僅處理該設備第一筆未處理的 Request（若有）
     * @return 是否成功處理 1 筆
     */
    @Transactional
    public boolean monitorUnacceptedRequestsByDevice(Long infraredId) {
        return requestRepository.findFirstUnacceptedByInfraredId(infraredId)
                .map(request -> {
                    try {
                        commandService.convertRequestToTask(request.getId(), request.getInfraredId());
                        return true;
                    } catch (Exception e) {
                        log.warn("[Monitor] 處理 InfraredRequest id={} 失敗：{}", request.getId(), e.getMessage(), e);
                        return false;
                    }
                })
                .orElse(false);
    }
}
