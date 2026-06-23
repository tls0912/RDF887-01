package com.czkuo.rdf88701.presentation.web.dto;

import jakarta.validation.constraints.Min;

public record ContainerDataRequest(
        @Min(0) Integer verifiedQuantity,
        @Min(0) Integer estimatedQuantity,
        @Min(0) Integer coverLayers,
        @Min(0) Integer productLayers,
        String  ocrText1,
        String  ocrText2,
        String  contentKind

) {}
