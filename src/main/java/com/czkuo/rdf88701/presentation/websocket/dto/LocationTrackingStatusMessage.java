package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
public class LocationTrackingStatusMessage {

    private Long containerMainId;
    private Long locationPointId;
    private LocalDateTime arrivedTime;

    private Instant snapshotTime;
}
