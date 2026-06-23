package com.czkuo.rdf88701.application.service;

import com.czkuo.rdf88701.application.service.command.GripperRequestCommandService;
import com.czkuo.rdf88701.domain.repository.GripperRequestRepository;
import com.czkuo.rdf88701.infra.entity.GripperRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gripper Request 處理服務
 * - 依據 Gripper 設備進行分組處理，將 Request 轉換為 Task
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GripperRequestMonitorService {

    private final GripperRequestRepository requestRepository;
    private final GripperRequestCommandService commandService;

    /**
     * 掃描所有未處理的 GripperRequest，依 Gripper ID 分組處理
     */
    @Transactional
    public void monitorUnacceptedRequestsByDevice() {
        List<GripperRequest> pendingRequests = requestRepository.findUnacceptedRequests();
        if (pendingRequests.isEmpty()) {
            return;
        }

        // 依 Gripper ID 分組（每台 Gripper 處理第一筆）
        Map<Long, List<GripperRequest>> groupedByGripperId =
                pendingRequests.stream().collect(Collectors.groupingBy(GripperRequest::getGripperId));

        for (Map.Entry<Long, List<GripperRequest>> entry : groupedByGripperId.entrySet()) {
            Long gripperId = entry.getKey();
            List<GripperRequest> requests = entry.getValue();

            GripperRequest firstRequest = requests.get(0); // 僅取第一筆

            try {
                commandService.convertRequestToTask(firstRequest.getId(), gripperId);
                log.info("[Monitor] 已轉換 GripperRequest id={} → Task", firstRequest.getId());
            } catch (Exception e) {
                log.warn("[Monitor] 處理 GripperRequest id={} 失敗：{}", firstRequest.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 僅處理指定 Gripper 第一筆未處理的 Request（若有）
     *
     * @param gripperId Gripper 裝置 ID
     * @return 是否成功處理 1 筆
     */
    @Transactional
    public boolean monitorUnacceptedRequestsByDevice(String gripperId) {
        return requestRepository.findFirstUnacceptedByDeviceId(gripperId)
                .map(request -> {
                    try {
                        commandService.convertRequestToTask(request.getId(), request.getGripperId());
                        return true;
                    } catch (Exception e) {
                        log.warn("[Monitor] 處理 GripperRequest id={} 失敗：{}", request.getId(), e.getMessage(), e);
                        return false;
                    }
                })
                .orElse(false);
    }
}
