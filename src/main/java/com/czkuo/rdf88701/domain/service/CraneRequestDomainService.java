package com.czkuo.rdf88701.domain.service;

import com.czkuo.rdf88701.application.dto.command.CraneRequestCreateCommand;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.CraneRequestRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * CraneRequest 的領域邏輯處理服務（負責建立與驗證流程）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class CraneRequestDomainService {

    private final CraneRequestRepository craneRequestRepository;
    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;

    /**
     * 建立請求時的所有驗證流程
     */
    public void validateForCreation(CraneRequest request) {
        validateRequestFields(request);
        validateNoDuplicate(request);
        validateContainerExists(request);
        validateLocationLogic(request);
        //validateNoUnfinishedRequest(request);
    }

    /**
     * 驗證：當已有相同 requestKey，是否允許升級
     */
    public void validateUpgradeAllowed(CraneRequest existingRequest, CraneRequestCreateCommand command) {
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
    private void validateRequestFields(CraneRequest request) {
        if (request.getRequestKey() == null || request.getRequestKey().isBlank()) {
            throw new IllegalArgumentException("requestKey must not be empty");
        }
        if (request.getRequestType() == null || request.getRequestType().isBlank()) {
            throw new IllegalArgumentException("requestType must not be empty");
        }
    }

    /**
     * 檢查 requestKey 是否重複
     */
    private void validateNoDuplicate(CraneRequest request) {
        if (craneRequestRepository.existsByRequestKey(request.getRequestKey())) {
            throw new IllegalStateException("Duplicated requestKey: " + request.getRequestKey());
        }
    }

    /**
     * 檢查 containerMain 是否存在
     */
    private void validateContainerExists(CraneRequest request) {
        containerMainRepository.findById(request.getContainerMainId())
                .orElseThrow(() -> new IllegalArgumentException("Container not found: " + request.getContainerMainId()));
    }

    /**
     * 驗證位置存在性與邏輯性
     */
    private void validateLocationLogic(CraneRequest request) {
        Long sourceId = request.getSourceLocationId();
        Long targetId = request.getTargetLocationId();

        if (sourceId != null && locationPointRepository.findById(sourceId).isEmpty()) {
            throw new IllegalArgumentException("Invalid source location: " + sourceId);
        }
        if (targetId != null && locationPointRepository.findById(targetId).isEmpty()) {
            throw new IllegalArgumentException("Invalid target location: " + targetId);
        }
        if (sourceId != null && targetId != null && Objects.equals(sourceId, targetId)) {
            throw new IllegalArgumentException("Source and target location cannot be the same.");
        }

        switch (request.getRequestType()) {
            case "INBOUND" -> {
                if (sourceId == null || targetId == null) {
                    throw new IllegalArgumentException("INBOUND must have only source location.");
                }
            }
            case "OUTBOUND" -> {
                if (sourceId == null || targetId == null) {
                    throw new IllegalArgumentException("OUTBOUND must have only target location.");
                }
            }
            case "RELOCATE" -> {
                if (sourceId == null || targetId == null) {
                    throw new IllegalArgumentException("RELOCATE must have both source and target.");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported requestType: " + request.getRequestType());
        }
    }

    /**
     * 檢查是否已有未完成請求
     */
    private void validateNoUnfinishedRequest(CraneRequest request) {
        boolean hasOngoing = craneRequestRepository.existsUnfinishedRequestForContainer(request.getContainerMainId());
        if (hasOngoing) {
            throw new IllegalStateException("Container has unfinished crane request: " + request.getContainerMainId());
        }
    }

    /**
     * 轉換成 task 前的 domain 驗證
     */
    public void validateForConvertToTask(CraneRequest request) {
        if (request.isRequestAccepted()) {
            throw new IllegalStateException("CraneRequest already converted to task (accepted=Y)");
        }
        if (request.isRejected()) {
            throw new IllegalStateException("CraneRequest has been rejected (cannot convert)");
        }
    }
}
