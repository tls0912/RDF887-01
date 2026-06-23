package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.application.dto.command.ExternalCraneRequestCreateCommand;
import com.czkuo.rdf88701.common.exception.BusinessException;
import com.czkuo.rdf88701.domain.repository.ContainerMainRepository;
import com.czkuo.rdf88701.domain.repository.LocationPointRepository;
import com.czkuo.rdf88701.infra.entity.ContainerMain;
import com.czkuo.rdf88701.infra.entity.LocationPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 外部系統建立 Crane Request（location name 版）
 */
@Service
@RequiredArgsConstructor
public class ExternalCraneRequestCommandService {

    private final ContainerMainRepository containerMainRepository;
    private final LocationPointRepository locationPointRepository;
    private final CraneRequestCommandService craneRequestCommandService;

    public Long create(ExternalCraneRequestCreateCommand externalCommand) {
        // 1. container code → container_main.id
        ContainerMain container = containerMainRepository.findByAliasCode(externalCommand.getContainerMainCode())
                .orElseThrow(() -> new BusinessException("Container not found: " + externalCommand.getContainerMainCode()));

        // 2. location name → location_point.id
        Long sourceLocationId = null;
        Long targetLocationId = null;

        if (externalCommand.getSourceLocationName() != null) {
            LocationPoint source = locationPointRepository.findByName(externalCommand.getSourceLocationName())
                    .orElseThrow(() -> new BusinessException("Source location not found: " + externalCommand.getSourceLocationName()));
            sourceLocationId = source.getId();
        }

        if (externalCommand.getTargetLocationName() != null) {
            LocationPoint target = locationPointRepository.findByName(externalCommand.getTargetLocationName())
                    .orElseThrow(() -> new BusinessException("Target location not found: " + externalCommand.getTargetLocationName()));
            targetLocationId = target.getId();
        }

        // 3. 呼叫內部 service 建立
        return craneRequestCommandService.createInternal(
                externalCommand.getRequestKey(),
                externalCommand.getRequestType(),
                externalCommand.getRequestSource(),
                externalCommand.getSourceRequestRef(),
                container.getId(),
                sourceLocationId,
                targetLocationId,
                externalCommand.getSourceLocationName(),
                externalCommand.getTargetLocationName(),
                externalCommand.getOperator(),
                externalCommand.getRemark(),
                externalCommand.getRawPayload()
        );
    }
}
