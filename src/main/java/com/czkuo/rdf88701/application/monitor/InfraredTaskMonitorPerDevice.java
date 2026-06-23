package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.InfraredHandshakeStateMachine;
import com.czkuo.rdf88701.application.service.query.InfraredTaskQueryService;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.infra.cache.InfraredCommandCache;
import com.czkuo.rdf88701.infra.cache.InfraredStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrared 單台任務監控處理器
 * - 檢查設備狀態與任務佇列
 * - 若設備就緒，推進握手流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredTaskMonitorPerDevice {

    private final InfraredTaskQueryService taskQueryService;
    private final InfraredStatusCache statusCache;
    private final InfraredCommandCache commandStatusCache;
    private final InfraredHandshakeStateMachine handshakeStateMachine;

    public void monitorSingleInfrared(String infraredName) {
        InfraredDeviceStatus deviceStatus = statusCache.getLatest(infraredName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) return;

        int infraredId = deviceStatus.getInfraredId();
        InfraredCommandStatus commandStatus = commandStatusCache.getLatest(infraredId);
        InfraredCommandStatus lastWrite = commandStatusCache.getLastWriteCommand(infraredId);
        if (commandStatus != null && lastWrite != null) {
            commandStatus.setLastWriteCommand(lastWrite);
        }

        // if (!deviceStatus.isInfraredStandby()) {
        //     //log.debug("[Monitor] Infrared '{}' 尚未進入 standby 狀態", infraredName);
        //     return;
        // }

        taskQueryService.findTopPriorityTaskByInfrared(infraredId).ifPresent(task -> {
            try {
                handshakeStateMachine.tick(task, deviceStatus, commandStatus);
            } catch (Exception e) {
                log.error("[Monitor] Infrared#{} 任務#{} 發生錯誤：{}", infraredId, task.getId(), e.getMessage(), e);
            }
        });
    }
}
