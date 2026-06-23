package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.InfraredRequestCreateCommand;
import com.czkuo.rdf88701.domain.factory.InfraredRequestFactory;
import com.czkuo.rdf88701.domain.factory.InfraredTaskFactory;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.domain.service.InfraredRequestDomainService;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * InfraredRequest 建立與轉換指令服務
 */
@Service
@RequiredArgsConstructor
public class InfraredRequestCommandService {

    private final InfraredRequestRepository requestRepository;
    private final InfraredTaskRepository taskRepository;
    private final InfraredRequestDomainService requestDomainService;

    /**
     * 建立新 request，支援版本升級與重複 key 保護
     */
    @Transactional
    public Long create(InfraredRequestCreateCommand command) {
        Optional<InfraredRequest> existing = requestRepository.findByRequestKey(command.getRequestKey());

        InfraredRequest entity;
        if (existing.isPresent()) {
            // 驗證是否允許升級
            requestDomainService.validateUpgradeAllowed(existing.get(), command);
            entity = InfraredRequestFactory.upgradeFrom(existing.get(), command.getRemark());
        } else {
            entity = InfraredRequestFactory.create(command);
            requestDomainService.validateForCreation(entity);
        }

        requestRepository.save(entity);
        return entity.getId();
    }

    /**
     * 將已建立的 request 轉換為 task
     */
    @Transactional
    public Long convertRequestToTask(Long requestId, Long infraredId) {
        InfraredRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("InfraredRequest not found: " + requestId));

        requestDomainService.validateForConvertToTask(request);

        InfraredTask task = InfraredTaskFactory.createFromRequest(request);

        taskRepository.save(task);
        request.markAsAccepted();
        requestRepository.update(request);

        return task.getId();
    }

    /**
     * 內部建立用（支援傳入 remark 與原始 payload）
     */
    @Transactional
    public Long createInternal(String requestKey,
                               String requestSource,
                               Long infraredId,
                               String taskType,
                               String operator,
                               String remark,
                               String rawPayload) {

        InfraredRequestCreateCommand command = new InfraredRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource(requestSource);
        command.setInfraredId(infraredId);
        command.setTaskType(taskType);
        command.setOperator(operator);
        command.setRemark(remark);
        command.setRawPayload(rawPayload);

        return create(command);
    }

    /**
     * 建立一筆由系統自動產生的測量請求
     */
    @Transactional
    public Long createSystemRequest(Long infraredId, String remark) {
        String requestKey = "SYS-" + infraredId + "-MEASURE-" + System.currentTimeMillis();

        InfraredRequestCreateCommand command = new InfraredRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource("SYSTEM");
        command.setInfraredId(infraredId);
        command.setTaskType("MEASURE");
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }
}
