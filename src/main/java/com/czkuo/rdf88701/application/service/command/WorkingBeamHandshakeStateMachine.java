package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WorkingBeam 握手狀態機
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
public class WorkingBeamHandshakeStateMachine {

    private final DefaultWorkingBeamHandshakeStrategy defaultStrategy;

    public void tick(WorkingBeamTask task,
                     WorkingBeamDeviceStatus deviceStatus,
                     WorkingBeamCommandStatus commandStatus) {
        // 預設策略進行流程處理，未來可擴充支援策略切換
        defaultStrategy.tick(task, deviceStatus, commandStatus);
    }
}
