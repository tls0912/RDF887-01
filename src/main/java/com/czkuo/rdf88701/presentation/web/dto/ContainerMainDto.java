package com.czkuo.rdf88701.presentation.web.dto;

import java.time.LocalDateTime;

public record ContainerMainDto(
        Long id,
        String carrierId,
        String containerType,
        String containerCode,
        String lotNo,
        String partNo,
        LocalDateTime createdTime,
        Integer verifiedQuantity,
        Integer estimatedQuantity,
        Integer coverLayers,
        Integer productLayers,
        String ocrText1,
        String ocrText2,
        String contentKind,
        Double trayThicknessMm
) {}
