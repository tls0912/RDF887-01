package com.czkuo.rdf88701.infra.event.model.plc.infrared;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * InfraredStatusUpdatedEvent
 * - 表示單一紅外線設備的狀態更新事件
 * - 用於事件推播、狀態記錄、或狀態同步處理
 */
@Getter
@ToString
@RequiredArgsConstructor
public class InfraredStatusUpdatedEvent {

    /** 紅外線設備 ID */
    private final int infraredId;

    /** 最新設備狀態（完整快照） */
    private final InfraredDeviceStatus deviceStatus;

    /** 判斷後的主狀態（如 IDLE、WAIT_CMD 等） */
    private final InfraredState currentState;
}
