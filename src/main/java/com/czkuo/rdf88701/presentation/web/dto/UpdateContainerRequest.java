package com.czkuo.rdf88701.presentation.web.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateContainerRequest(
        String containerType,

        @Size(max = 50)
        String containerCode,

        @Size(max = 50)
        String lotNo,

        @Size(max = 50)
        String partNo,

        ContainerDataRequest  data,

        @Positive
        Double trayThicknessMm
) {}
