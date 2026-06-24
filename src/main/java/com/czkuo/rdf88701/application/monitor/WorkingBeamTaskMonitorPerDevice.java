package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.WorkingBeamHandshakeStateMachine;
import com.czkuo.rdf88701.application.service.query.WorkingBeamTaskQueryService;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.infra.cache.WorkingBeamCommandCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WorkingBeam 單台任務監控處理器
 * - 檢查設備狀態與任務佇列
 * - 若設備就緒，推進握手流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamTaskMonitorPerDevice {

    private final WorkingBeamTaskQueryService taskQueryService;
    private final WorkingBeamStatusCache statusCache;
    private final WorkingBeamCommandCache commandStatusCache;
    private final WorkingBeamHandshakeStateMachine handshakeStateMachine;

    public void monitorSingleBeam(String beamName) {
        WorkingBeamDeviceStatus deviceStatus = statusCache.getLatest(beamName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) return;

        int beamId = deviceStatus.getWorkingBeamId();
        WorkingBeamCommandStatus commandStatus = commandStatusCache.getLatest(beamId);
        WorkingBeamCommandStatus lastWrite = commandStatusCache.getLastWriteCommand(beamId);
        if (commandStatus != null && lastWrite != null) {
            commandStatus.setLastWriteCommand(lastWrite);
        }

        // if (!deviceStatus.isTransferStandby()) {
        //    //log.debug("[Monitor] WorkingBeam '{}' 尚未進入 standby 狀態", beamName);
        //    return;
        // }

        taskQueryService.findTopPriorityTaskByWorkingBeam(beamId).ifPresent(task -> {
            try {
                handshakeStateMachine.tick(task, deviceStatus, commandStatus);
            } catch (Exception e) {
                log.error("[Monitor] WorkingBeam#{} 任務#{} 發生錯誤：{}", beamId, task.getId(), e.getMessage(), e);
            }
        });
    }
}
