package com.czkuo.rdf88701.application.service.command;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.infra.entity.TransferTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transfer 握手狀態機
 * - 提供統一進入點，推進任務握手流程
 * - 可支援多策略切換（目前使用預設策略）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferHandshakeStateMachine {

    private final DefaultTransferHandshakeStrategy defaultStrategy;

    public void tick(TransferTask task,
                     TransferDeviceStatus deviceStatus,
                     TransferCommandStatus commandStatus) {
        // 使用預設策略執行握手流程
        defaultStrategy.tick(task, deviceStatus, commandStatus);
    }
}
