package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 庫位狀態單筆推播訊息。
 *
 * <p>用於 `/topic/location/point/status`，表示庫位鎖定、占用、預約、啟用狀態與
 * 快照時間。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
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
