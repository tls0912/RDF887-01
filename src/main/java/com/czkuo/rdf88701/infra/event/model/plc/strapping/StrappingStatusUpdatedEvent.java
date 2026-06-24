package com.czkuo.rdf88701.infra.event.model.plc.strapping;

import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * StrappingStatusUpdatedEvent
 * - 表示單一 Strapping 裝置的設備狀態更新事件
 * - 用於事件推播、狀態記錄、或狀態同步流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class StrappingStatusUpdatedEvent {

    /** Strapping 裝置 ID */
    private final int strappingId;

    /** 最新設備狀態資料（完整快照） */
    private final StrappingDeviceStatus deviceStatus;

    /** 最新判斷後的狀態（如 IDLE、PROCESSING 等） */
    private final StrappingState currentState;
}
