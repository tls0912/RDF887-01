package com.czkuo.rdf88701.infra.event.model.plc.gripper;

import com.czkuo.rdf88701.domain.plc.state.common.RunningSubStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperState;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Gripper 最新狀態更新事件
 * - 含裝置層級的物理狀態（DeviceStatus）
 * - 含邏輯層級的業務狀態（StateMachine）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@AllArgsConstructor
public class GripperStatusUpdatedEvent {

    private final int gripperId; // 哪個 Gripper
    private final GripperDeviceStatus deviceStatus; // 原始 PLC 解析結果
    private final GripperState stateMachineState; // 業務狀態流程（如 READY_TO_SEND, RUNNING）
}
