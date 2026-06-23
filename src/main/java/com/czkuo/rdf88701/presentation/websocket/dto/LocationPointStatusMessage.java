package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * WebSocket 推播用 DTO：儲位狀態
 */
@Data
@Builder
public class LocationPointStatusMessage {

    private Long locationPointId;
    private boolean isLocked;
    private boolean isOccupied;
    private boolean isReserved;
    private boolean isEnabled;

    private Instant snapshotTime;
}