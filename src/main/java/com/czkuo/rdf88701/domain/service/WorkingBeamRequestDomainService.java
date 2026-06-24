package com.czkuo.rdf88701.domain.service;

import com.czkuo.rdf88701.application.dto.command.WorkingBeamRequestCreateCommand;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * WorkingBeamRequest 的領域邏輯處理服務（建立與轉換驗證）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已修改，// 註解已依現有實作校正。
 */
@Service
@RequiredArgsConstructor
public class WorkingBeamRequestDomainService {

    private final WorkingBeamRequestRepository requestRepository;

    /**
     * 建立請求時的所有驗證流程
     */
    public void validateForCreation(WorkingBeamRequest request) {
        validateRequestFields(request);
        validateNoDuplicate(request);
        // 目前僅保留後續處理入口，尚未實作額外流程。
    }

    /**
     * 驗證：當已有相同 requestKey，是否允許升級
     */
    public void validateUpgradeAllowed(WorkingBeamRequest existingRequest, WorkingBeamRequestCreateCommand command) {
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
    private void validateRequestFields(WorkingBeamRequest request) {
        if (request.getRequestKey() == null || request.getRequestKey().isBlank()) {
            throw new IllegalArgumentException("requestKey must not be empty");
        }
        if (request.getDirection() == null || request.getDirection().isBlank()) {
            throw new IllegalArgumentException("direction must not be empty");
        }
    }

    /**
     * 檢查 requestKey 是否重複
     */
    private void validateNoDuplicate(WorkingBeamRequest request) {
        if (requestRepository.existsByRequestKey(request.getRequestKey())) {
            throw new IllegalStateException("Duplicated requestKey: " + request.getRequestKey());
        }
    }

    /**
     * 轉換成 task 前的驗證
     */
    public void validateForConvertToTask(WorkingBeamRequest request) {
        if (request.isRequestAccepted()) {
            throw new IllegalStateException("WorkingBeamRequest already converted to task (accepted=Y)");
        }
        if (request.isRejected()) {
            throw new IllegalStateException("WorkingBeamRequest has been rejected (cannot convert)");
        }
    }
}
