package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.infra.entity.CraneTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Crane 握手狀態機
 * - 提供統一進入點，推進任務握手流程
 * - 可切換多策略（目前使用預設策略）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CraneHandshakeStateMachine {

    private final DefaultCraneHandshakeStrategy defaultStrategy;

    public void tick(CraneTask task, CraneDeviceStatus status, CraneCommandStatus commandStatus) {
        // 未來若支援多策略可在此動態選擇
        defaultStrategy.tick(task, status, commandStatus);
    }
}
