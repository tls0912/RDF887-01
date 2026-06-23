package com.czkuo.rdf88701.domain.service;

import com.czkuo.rdf88701.application.dto.command.InfraredRequestCreateCommand;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * InfraredRequest 的領域邏輯處理服務（建立與轉換驗證）
 */
@Service
@RequiredArgsConstructor
public class InfraredRequestDomainService {

    private final InfraredRequestRepository requestRepository;

    /**
     * 建立請求時的所有驗證流程
     */
    public void validateForCreation(InfraredRequest request) {
        validateRequestFields(request);
        validateNoDuplicate(request);
        // TODO: 可擴充其他檢查條件（如 sensor 狀態、流程一致性）
    }

    /**
     * 驗證：當已有相同 requestKey，是否允許升級
     */
    public void validateUpgradeAllowed(InfraredRequest existingRequest, InfraredRequestCreateCommand command) {
        if (existingRequest.isRequestAccepted()) {
            throw new IllegalStateException("Existing request already accepted (cannot upgrade)");
        }
        if (existingRequest.isRejected()) {
            throw new IllegalStateException("Existing request has been rejected (cannot upgrade)");
        }
    }

    /**
     * Request 基本欄位檢查
     */
    private void validateRequestFields(InfraredRequest request) {
        if (request.getRequestKey() == null || request.getRequestKey().isBlank()) {
            throw new IllegalArgumentException("requestKey must not be empty");
        }
        if (request.getTaskType() == null || request.getTaskType().isBlank()) {
            throw new IllegalArgumentException("taskType must not be empty");
        }
    }

    /**
     * 檢查 requestKey 是否重複
     */
    private void validateNoDuplicate(InfraredRequest request) {
        if (requestRepository.existsByRequestKey(request.getRequestKey())) {
            throw new IllegalStateException("Duplicated requestKey: " + request.getRequestKey());
        }
    }

    /**
     * 轉換成 task 前的驗證
     */
    public void validateForConvertToTask(InfraredRequest request) {
        if (request.isRequestAccepted()) {
            throw new IllegalStateException("InfraredRequest already converted to task (accepted=Y)");
        }
        if (request.isRejected()) {
            throw new IllegalStateException("InfraredRequest has been rejected (cannot convert)");
        }
    }
}
