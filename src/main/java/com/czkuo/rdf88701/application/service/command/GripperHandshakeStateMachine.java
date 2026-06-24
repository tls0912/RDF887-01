package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.infra.entity.GripperTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gripper 握手狀態機
 * - 提供統一進入點，推進任務握手流程
 * - 可支援多策略切換（目前使用預設策略）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperHandshakeStateMachine {

    private final DefaultGripperHandshakeStrategy defaultStrategy;

    public void tick(GripperTask task,
                     GripperDeviceStatus deviceStatus,
                     GripperCommandStatus commandStatus) {
        // 使用預設策略執行握手流程
        defaultStrategy.tick(task, deviceStatus, commandStatus);
    }
}
