package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.infra.entity.InfraredTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrared 握手狀態機
 * - 提供統一進入點，推進任務握手流程
 * - 可支援多策略切換（目前使用預設策略）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredHandshakeStateMachine {

    private final DefaultInfraredHandshakeStrategy defaultStrategy;

    public void tick(InfraredTask task,
                     InfraredDeviceStatus deviceStatus,
                     InfraredCommandStatus commandStatus) {
        // 預設策略進行流程處理，未來可擴充支援策略切換
        defaultStrategy.tick(task, deviceStatus, commandStatus);
    }
}
