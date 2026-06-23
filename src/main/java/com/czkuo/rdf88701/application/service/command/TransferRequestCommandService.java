package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.TransferRequestCreateCommand;
import com.czkuo.rdf88701.domain.factory.TransferRequestFactory;
import com.czkuo.rdf88701.domain.factory.TransferTaskFactory;
import com.czkuo.rdf88701.domain.repository.TransferRequestRepository;
import com.czkuo.rdf88701.domain.repository.TransferTaskRepository;
import com.czkuo.rdf88701.domain.service.TransferRequestDomainService;
import com.czkuo.rdf88701.infra.entity.TransferRequest;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TransferRequest 建立與轉換指令服務
 */
@Service
@RequiredArgsConstructor
public class TransferRequestCommandService {

    private final TransferRequestRepository requestRepository;
    private final TransferTaskRepository taskRepository;
    private final TransferRequestDomainService requestDomainService;

    /**
     * 建立新 TransferRequest，支援版本升級與重複 key 保護
     */
    @Transactional
    public Long create(TransferRequestCreateCommand command) {
        Optional<TransferRequest> existing = requestRepository.findByRequestKey(command.getRequestKey());

        TransferRequest entity;
        if (existing.isPresent()) {
            requestDomainService.validateUpgradeAllowed(existing.get(), command);
            entity = TransferRequestFactory.upgradeFrom(existing.get(), command.getRemark());
        } else {
            entity = TransferRequestFactory.create(command);
            requestDomainService.validateForCreation(entity);
        }

        requestRepository.save(entity);
        return entity.getId();
    }

    /**
     * 將已建立的 TransferRequest 轉換為 TransferTask
     */
    @Transactional
    public Long convertRequestToTask(Long requestId, Long transferId) {
        TransferRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("TransferRequest not found: " + requestId));

        requestDomainService.validateForConvertToTask(request);

        TransferTask task = TransferTaskFactory.createFromRequest(request);

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
                               Long transferId,
                               String taskType,
                               Long sourceLocationId,
                               Long targetLocationId,
                               String sourceLocationName,
                               String targetLocationName,
                               String operator,
                               String remark,
                               String rawPayload) {

        TransferRequestCreateCommand command = new TransferRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource(requestSource);
        command.setTransferId(transferId);
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
     * 建立一筆由系統自動產生的 TransferRequest
     */
    @Transactional
    public Long createSystemRequest(Long transferId,
                                    String taskType,
                                    Long sourceLocationId,
                                    Long targetLocationId,
                                    String sourceLocationName,
                                    String targetLocationName,
                                    String remark) {

        String requestKey = "SYS-" + transferId + "-" + taskType + "-" + System.currentTimeMillis();

        TransferRequestCreateCommand command = new TransferRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource("SYSTEM");
        command.setTransferId(transferId);
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
