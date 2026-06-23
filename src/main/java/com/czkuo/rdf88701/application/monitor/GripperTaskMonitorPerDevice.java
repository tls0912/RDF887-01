package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.GripperHandshakeStateMachine;
import com.czkuo.rdf88701.application.service.query.GripperTaskQueryService;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcGripperBitWriter;
import com.czkuo.rdf88701.infra.cache.GripperCommandCache;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gripper 單台任務監控處理器
 * - 檢查設備狀態與任務佇列
 * - 若設備就緒，推進握手流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperTaskMonitorPerDevice {

    private final GripperTaskQueryService taskQueryService;
    private final GripperStatusCache statusCache;
    private final GripperCommandCache commandStatusCache;
    private final GripperHandshakeStateMachine handshakeStateMachine;
    private final PlcGripperBitWriter bitWriter;

    public void monitorSingleGripper(String gripperName) {
        GripperDeviceStatus deviceStatus = statusCache.getLatest(gripperName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) return;

        int gripperId = deviceStatus.getGripperId();
        GripperCommandStatus commandStatus = commandStatusCache.getLatest(gripperId);
        GripperCommandStatus lastWrite = commandStatusCache.getLastWriteCommand(gripperId);
        if (commandStatus != null && lastWrite != null) {
            commandStatus.setLastWriteCommand(lastWrite);
        }

        // if (!deviceStatus.isTransferStandby()) {
        //     //log.debug("[Monitor] Gripper '{}' 尚未進入 standby 狀態", gripperName);
        //     return;
        // }

        taskQueryService.findTopPriorityTaskByGripper(gripperId).ifPresentOrElse(task -> {
            try {
                handshakeStateMachine.tick(task, deviceStatus, commandStatus);
            } catch (Exception e) {
                log.error("[Monitor] Gripper#{} 任務#{} 發生錯誤：{}", gripperId, task.getId(), e.getMessage(), e);
            }
        }, new Runnable() {
            @Override
            public void run() {
                if (commandStatus == null || !commandStatus.isTransferCmdReq())
                    return;
                setCmdReq(gripperId, false);
                setCompAck(gripperId, true);
                setCompAck(gripperId, false);
                log.error("[Monitor] Gripper#{} 無任務且Transfer CMD Req=ON，自動復歸訊號", gripperId);
            }
        });
    }

    private void setCmdReq(int gripperId, boolean value) {
        bitWriter.writeGripperCmdReq(gripperId, value);
    }

    private void setCompAck(int gripperId, boolean value) {
        bitWriter.writeGripperCompAck(gripperId, value);
    }


}
