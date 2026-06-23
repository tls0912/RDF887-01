package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.GripperRequestCreateCommand;
import com.czkuo.rdf88701.domain.factory.GripperRequestFactory;
import com.czkuo.rdf88701.domain.factory.GripperTaskFactory;
import com.czkuo.rdf88701.domain.repository.GripperRequestRepository;
import com.czkuo.rdf88701.domain.repository.GripperTaskRepository;
import com.czkuo.rdf88701.domain.service.GripperRequestDomainService;
import com.czkuo.rdf88701.infra.entity.GripperRequest;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * GripperRequest 建立與轉換指令服務
 */
@Service
@RequiredArgsConstructor
public class GripperRequestCommandService {

    private final GripperRequestRepository requestRepository;
    private final GripperTaskRepository taskRepository;
    private final GripperRequestDomainService requestDomainService;

    /**
     * 建立新 GripperRequest，支援版本升級與重複 key 保護
     */
    @Transactional
    public Long create(GripperRequestCreateCommand command) {
        Optional<GripperRequest> existing = requestRepository.findByRequestKey(command.getRequestKey());

        GripperRequest entity;
        if (existing.isPresent()) {
            requestDomainService.validateUpgradeAllowed(existing.get(), command);
            entity = GripperRequestFactory.upgradeFrom(existing.get(), command.getRemark());
        } else {
            entity = GripperRequestFactory.create(command);
            requestDomainService.validateForCreation(entity);
        }

        requestRepository.save(entity);
        return entity.getId();
    }

    /**
     * 將已建立的 GripperRequest 轉換為 GripperTask
     */
    @Transactional
    public Long convertRequestToTask(Long requestId, Long gripperId) {
        GripperRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("GripperRequest not found: " + requestId));

        requestDomainService.validateForConvertToTask(request);

        GripperTask task = GripperTaskFactory.createFromRequest(request);

        taskRepository.save(task);
        request.markAsAccepted();
        requestRepository.update(request);

        return task.getId();
    }

    /**
     * 內部建立用（支援傳入原始欄位）
     */
    @Transactional
    public Long createInternal(String requestKey,
                               String requestSource,
                               Long gripperId,
                               String taskType,
                               Long sourceLocationId,
                               Long targetLocationId,
                               String sourceLocationName,
                               String targetLocationName,
                               String operator,
                               String remark,
                               String rawPayload) {

        GripperRequestCreateCommand command = new GripperRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource(requestSource);
        command.setGripperId(gripperId);
        command.setTaskType(taskType);
        command.setSourceLocationId(sourceLocationId);
        command.setTargetLocationId(targetLocationId);
        command.setSourceLocationName(sourceLocationName);
        command.setTargetLocationName(targetLocationName);
        command.setOperator(operator);
        command.setRemark(remark);
        command.setRawPayload(rawPayload);

        return create(command);
    }

    /**
     * 建立一筆由系統自動產生的 GripperRequest
     */
    @Transactional
    public Long createSystemRequest(Long gripperId,
                                    String taskType,
                                    Long sourceLocationId,
                                    Long targetLocationId,
                                    String sourceLocationName,
                                    String targetLocationName,
                                    String remark) {

        String requestKey = "SYS-" + gripperId + "-" + taskType + "-" + System.currentTimeMillis();

        GripperRequestCreateCommand command = new GripperRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource("SYSTEM");
        command.setGripperId(gripperId);
        command.setTaskType(taskType);
        command.setSourceLocationId(sourceLocationId);
        command.setTargetLocationId(targetLocationId);
        command.setSourceLocationName(sourceLocationName);
        command.setTargetLocationName(targetLocationName);
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }
}
