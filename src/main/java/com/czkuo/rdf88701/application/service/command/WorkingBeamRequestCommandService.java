package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.WorkingBeamRequestCreateCommand;
import com.czkuo.rdf88701.domain.factory.WorkingBeamRequestFactory;
import com.czkuo.rdf88701.domain.factory.WorkingBeamTaskFactory;
import com.czkuo.rdf88701.domain.repository.WorkingBeamRequestRepository;
import com.czkuo.rdf88701.domain.repository.WorkingBeamTaskRepository;
import com.czkuo.rdf88701.domain.service.WorkingBeamRequestDomainService;
import com.czkuo.rdf88701.infra.entity.WorkingBeamRequest;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * WorkingBeamRequest 建立與轉換指令服務
 */
@Service
@RequiredArgsConstructor
public class WorkingBeamRequestCommandService {

    private final WorkingBeamRequestRepository requestRepository;
    private final WorkingBeamTaskRepository taskRepository;
    private final WorkingBeamRequestDomainService requestDomainService;

    /**
     * 建立新 request，支援版本升級與重複 key 保護
     */
    @Transactional
    public Long create(WorkingBeamRequestCreateCommand command) {
        Optional<WorkingBeamRequest> existing = requestRepository.findByRequestKey(command.getRequestKey());

        WorkingBeamRequest entity;
        if (existing.isPresent()) {
            // 驗證是否允許升級
            requestDomainService.validateUpgradeAllowed(existing.get(), command);
            entity = WorkingBeamRequestFactory.upgradeFrom(existing.get(), command.getRemark());
        } else {
            entity = WorkingBeamRequestFactory.create(command);
            requestDomainService.validateForCreation(entity);
        }

        requestRepository.save(entity);
        return entity.getId();
    }

    /**
     * 將已建立的 request 轉換為 task
     */
    @Transactional
    public Long convertRequestToTask(Long requestId, Long workingBeamId) {
        WorkingBeamRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("WorkingBeamRequest not found: " + requestId));

        requestDomainService.validateForConvertToTask(request);

        WorkingBeamTask task = WorkingBeamTaskFactory.createFromRequest(request);

        taskRepository.save(task);
        request.markAsAccepted();
        requestRepository.update(request);

        return task.getId();
    }

    /**
     * 內部建立用（支援傳入位置名稱）
     */
    @Transactional
    public Long createInternal(String requestKey,
                               String requestSource,
                               Long workingBeamId,
                               String direction,
                               String operator,
                               String remark,
                               String rawPayload) {

        WorkingBeamRequestCreateCommand command = new WorkingBeamRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource(requestSource);
        command.setWorkingBeamId(workingBeamId);
        command.setDirection(direction);
        command.setOperator(operator);
        command.setRemark(remark);
        command.setRawPayload(rawPayload);

        return create(command);
    }

    /**
     * 建立一筆由系統自動產生的搬運請求
     */
    @Transactional
    public Long createSystemRequest(Long workingBeamId, String direction, String remark) {
        String requestKey = "SYS-" + workingBeamId + "-" + direction + "-" + System.currentTimeMillis();

        WorkingBeamRequestCreateCommand command = new WorkingBeamRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestSource("SYSTEM");
        command.setWorkingBeamId(workingBeamId);
        command.setDirection(direction);
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }
}
