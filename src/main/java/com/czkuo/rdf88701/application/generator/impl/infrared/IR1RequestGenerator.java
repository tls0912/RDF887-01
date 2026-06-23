package com.czkuo.rdf88701.application.generator.impl.infrared;

import com.czkuo.rdf88701.application.generator.InfraredRequestGenerator;
import com.czkuo.rdf88701.domain.repository.InfraredRequestRepository;
import com.czkuo.rdf88701.domain.repository.InfraredTaskRepository;
import com.czkuo.rdf88701.infra.entity.InfraredRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * IR1RequestGenerator
 * - 為 Infrared#1 裝置產生一筆預設的 MEASURE 請求
 */
@Slf4j
//@Component("IR1")
@RequiredArgsConstructor
public class IR1RequestGenerator implements InfraredRequestGenerator {

    private final InfraredRequestRepository requestRepository;
    private final InfraredTaskRepository taskRepository;

    @Override
    public Optional<Long> generateRequest(Long infraredId) {
        // 避免重複產生請求
        if (requestRepository.existsUnfinishedRequestForInfrared(infraredId)
                || taskRepository.existsUnfinishedTaskForInfrared(infraredId)) {
            //log.debug("[IR1] Infrared#{} 已有未完成請求或任務，略過", infraredId);
            return Optional.empty();
        }

        // 模擬建立條件通過
        if (!checkCondition(infraredId)) return Optional.empty();

        // 建立新請求
        InfraredRequest request = new InfraredRequest();
        request.setRequestKey(UUID.randomUUID().toString());
        request.setVersion(1);
        request.setRequestSource("SYSTEM");
        request.setInfraredId(infraredId);
        request.setTaskType("MEASURE");
        request.setAccepted("N");
        request.setRequestTime(LocalDateTime.now());
        request.setCreatedTime(LocalDateTime.now());

        boolean success = requestRepository.save(request);
        if (success) {
            log.info("[IR1] 建立 InfraredRequest 成功, ID={}, Key={}", request.getId(), request.getRequestKey());
            return Optional.of(request.getId());
        } else {
            log.warn("[IR1] 建立 InfraredRequest 失敗");
            return Optional.empty();
        }
    }

    private boolean checkCondition(Long infraredId) {
        // TODO: 加入感測器、流程條件等
        return true;
    }
}
