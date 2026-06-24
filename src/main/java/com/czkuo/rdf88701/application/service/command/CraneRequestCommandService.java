package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.CraneRequestCreateCommand;
import com.czkuo.rdf88701.domain.factory.CraneRequestFactory;
import com.czkuo.rdf88701.domain.factory.CraneTaskFactory;
import com.czkuo.rdf88701.domain.repository.CraneRequestRepository;
import com.czkuo.rdf88701.domain.repository.CraneTaskRepository;
import com.czkuo.rdf88701.domain.service.CraneRequestDomainService;
import com.czkuo.rdf88701.infra.entity.CraneRequest;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CraneRequest 建立與轉換指令服務
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class CraneRequestCommandService {

    private final CraneRequestRepository craneRequestRepository;
    private final CraneTaskRepository craneTaskRepository;
    private final CraneRequestDomainService craneRequestDomainService;

    /**
     * 建立新 request，支援版本升級與重複 key 保護
     */
    @Transactional
    public Long create(CraneRequestCreateCommand command) {
        var existing = craneRequestRepository.findByRequestKey(command.getRequestKey());

        CraneRequest entity;
        if (existing.isPresent()) {
            // policy check
            craneRequestDomainService.validateUpgradeAllowed(existing.get(), command);

            // 通過驗證，執行升級
            entity = CraneRequestFactory.upgradeFrom(existing.get(), command.getRemark());
        } else {
            entity = CraneRequestFactory.create(command);
            craneRequestDomainService.validateForCreation(entity);
        }

        craneRequestRepository.save(entity);
        return entity.getId();
    }

    /**
     * 將已建立的 request 轉換為 task
     */
    @Transactional
    public Long convertRequestToTask(Long requestId, String craneId) {
        CraneRequest request = craneRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("CraneRequest not found: " + requestId));

        craneRequestDomainService.validateForConvertToTask(request);

        CraneTask task = CraneTaskFactory.createFromRequest(request, craneId);

        craneTaskRepository.save(task);
        request.markAsAccepted();
        craneRequestRepository.update(request);

        return task.getId();
    }

    /**
     * 外部系統 create 專用，支援 location name
     */
    @Transactional
    public Long createInternal(String requestKey,
                               String requestType,
                               String requestSource,
                               String sourceRequestRef,
                               Long containerMainId,
                               Long sourceLocationId,
                               Long targetLocationId,
                               String sourceLocationName,
                               String targetLocationName,
                               String operator,
                               String remark,
                               String rawPayload) {

        CraneRequestCreateCommand command = new CraneRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestType(requestType);
        command.setRequestSource(requestSource);
        command.setSourceRequestRef(sourceRequestRef);
        command.setContainerMainId(containerMainId);
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
     * 建立自動入庫任務（INBOUND）
     * - containerMainId：目標容器
     * - sourceLocationId：來源站點（如 Site#15）
     * - targetLocationId：入庫儲位（如 B1-L1）
     * - remark：自定備註，例如 "AUTO_INBOUND_SITE15"
     */
    @Transactional
    public Long createInboundRequest(Long containerMainId,
                                     Long sourceLocationId,
                                     Long targetLocationId,
                                     String remark) {

        String requestKey = "INBOUND-" + containerMainId + "-" + sourceLocationId + "-" + targetLocationId + "-" + System.currentTimeMillis();

        CraneRequestCreateCommand command = new CraneRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestType("INBOUND");
        command.setRequestSource("SYSTEM");
        command.setContainerMainId(containerMainId);
        command.setSourceLocationId(sourceLocationId);
        command.setTargetLocationId(targetLocationId);
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }

    /**
     * 建立自動出庫任務（OUTBOUND）
     * - containerMainId：目標容器
     * - sourceLocationId：出庫儲位
     * - targetLocationId：目標站點（如 Site#9）
     * - remark：自定備註，例如 "AUTO_OUTBOUND_SITE9"
     */
    @Transactional
    public Long createOutboundRequest(Long containerMainId,
                                      Long sourceLocationId,
                                      Long targetLocationId,
                                      String remark) {

        String requestKey = "OUTBOUND-" + containerMainId + "-" + sourceLocationId + "-" + targetLocationId + "-" + System.currentTimeMillis();

        CraneRequestCreateCommand command = new CraneRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestType("OUTBOUND");
        command.setRequestSource("SYSTEM");
        command.setContainerMainId(containerMainId);
        command.setSourceLocationId(sourceLocationId);
        command.setTargetLocationId(targetLocationId);
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }

    /**
     * 建立一筆自動搬移（Relocate）任務的 CraneRequest
     * - 專用於 Auto Walk 或系統自動任務
     * - 僅提供 container ID + 目標儲位，來源儲位由系統推導
     *
     * @param containerMainId 要搬移的容器 ID（container_main.id）
     * @param sourceLocationId 搬移目標儲位 ID（location_point.id）
     * @param targetLocationId 搬移目標儲位 ID（location_point.id）
     * @param remark 備註內容（例如：AUTO_WALK_RANDOM）
     * @return 建立成功的 CraneRequest ID
     */
    @Transactional
    public Long createRelocateRequest(Long containerMainId, Long sourceLocationId, Long targetLocationId, String remark) {
        String requestKey = "RELOCATE-" + containerMainId + "-" + sourceLocationId + "-" + targetLocationId + "-" + System.currentTimeMillis();

        CraneRequestCreateCommand command = new CraneRequestCreateCommand();
        command.setRequestKey(requestKey);
        command.setRequestType("RELOCATE");
        command.setRequestSource("SYSTEM");
        command.setContainerMainId(containerMainId);
        command.setSourceLocationId(sourceLocationId);
        command.setTargetLocationId(targetLocationId);
        command.setOperator("SYSTEM");
        command.setRemark(remark);

        return create(command);
    }
}
