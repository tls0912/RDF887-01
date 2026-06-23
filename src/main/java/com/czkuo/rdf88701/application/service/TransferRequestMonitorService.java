package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.service.command.TransferRequestCommandService;
import com.czkuo.rdf88701.domain.repository.TransferRequestRepository;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transfer Request 處理服務
 * - 依據 Transfer 設備進行分組處理，將 Request 轉換為 Task
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferRequestMonitorService {

    private final TransferRequestRepository requestRepository;
    private final TransferRequestCommandService commandService;

    /**
     * 掃描所有未處理的 TransferRequest，依 Transfer ID 分組處理
     */
    @Transactional
    public void monitorUnacceptedRequestsByDevice() {
        List<TransferRequest> pendingRequests = requestRepository.findUnacceptedRequests();
        if (pendingRequests.isEmpty()) {
            return;
        }

        // 依 Transfer ID 分組（每台 Transfer 處理第一筆）
        Map<Long, List<TransferRequest>> groupedByTransferId =
                pendingRequests.stream().collect(Collectors.groupingBy(TransferRequest::getTransferId));

        for (Map.Entry<Long, List<TransferRequest>> entry : groupedByTransferId.entrySet()) {
            Long transferId = entry.getKey();
            List<TransferRequest> requests = entry.getValue();

            TransferRequest firstRequest = requests.get(0); // 僅取第一筆

            try {
                commandService.convertRequestToTask(firstRequest.getId(), transferId);
                log.info("[Monitor] 已轉換 TransferRequest id={} → Task", firstRequest.getId());
            } catch (Exception e) {
                log.warn("[Monitor] 處理 TransferRequest id={} 失敗：{}", firstRequest.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 僅處理指定 Transfer 第一筆未處理的 Request（若有）
     *
     * @param transferId Transfer 裝置 ID
     * @return 是否成功處理 1 筆
     */
    @Transactional
    public boolean monitorUnacceptedRequestsByDevice(String transferId) {
        return requestRepository.findFirstUnacceptedByDeviceId(transferId)
                .map(request -> {
                    try {
                        commandService.convertRequestToTask(request.getId(), request.getTransferId());
                        return true;
                    } catch (Exception e) {
                        log.warn("[Monitor] 處理 TransferRequest id={} 失敗：{}", request.getId(), e.getMessage(), e);
                        return false;
                    }
                })
                .orElse(false);
    }
}
