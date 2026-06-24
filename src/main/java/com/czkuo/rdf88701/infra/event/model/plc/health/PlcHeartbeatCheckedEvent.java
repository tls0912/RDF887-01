package com.czkuo.rdf88701.infra.event.model.plc.health;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * 心跳檢查完成事件（成功或失敗皆會發送）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlcHeartbeatCheckedEvent implements Serializable {
    private String deviceName;
    private Instant timestamp;
    private boolean success;
    private long durationMs;
}
