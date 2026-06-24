package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Data
@Builder
public class LocationTrackingStatusMessage {

    private Long containerMainId;
    private Long locationPointId;
    private LocalDateTime arrivedTime;

    private Instant snapshotTime;
}
