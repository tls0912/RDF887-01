package com.czkuo.rdf88701.application.monitor;

import com.czkuo.rdf88701.application.service.command.TransferHandshakeStateMachine;
import com.czkuo.rdf88701.application.service.query.TransferTaskQueryService;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.infra.adapter.plc.writer.PlcTransferBitWriter;
import com.czkuo.rdf88701.infra.cache.TransferCommandCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transfer 單台任務監控處理器
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
public class TransferTaskMonitorPerDevice {

    private final TransferTaskQueryService taskQueryService;
    private final TransferStatusCache statusCache;
    private final TransferCommandCache commandStatusCache;
    private final TransferHandshakeStateMachine handshakeStateMachine;
    private final PlcTransferBitWriter bitWriter;

    public void monitorSingleTransfer(String transferName) {
        TransferDeviceStatus deviceStatus = statusCache.getLatest(transferName);
        if (deviceStatus == null || !deviceStatus.isValidAndComplete(3)) return;

        int transferId = deviceStatus.getTransferId();
        TransferCommandStatus commandStatus = commandStatusCache.getLatest(transferId);
        TransferCommandStatus lastWrite = commandStatusCache.getLastWriteCommand(transferId);
        if (commandStatus != null && lastWrite != null) {
            commandStatus.setLastWriteCommand(lastWrite);
        }

        // if (!deviceStatus.isTransferStandby()) {
        //     //log.debug("[Monitor] Transfer '{}' 尚未進入 standby 狀態", transferName);
        //     return;
        // }

        taskQueryService.findTopPriorityTaskByTransfer(transferId).ifPresentOrElse(task -> {
            try {
                handshakeStateMachine.tick(task, deviceStatus, commandStatus);
            } catch (Exception e) {
                log.error("[Monitor] Transfer#{} 任務#{} 發生錯誤：{}", transferId, task.getId(), e.getMessage(), e);
            }
        }, new Runnable() {
            @Override
            public void run() {
                if (commandStatus == null || !commandStatus.isTransferCmdReq())
                    return;
                setCmdReq(transferId, false);
                setCompAck(transferId, true);
                setCompAck(transferId, false);
                log.error("[Monitor] Transfer#{} 無任務且Transfer CMD Req=ON，自動復歸訊號", transferId);
            }
        });
    }

    private void setCmdReq(int gripperId, boolean value) {
        bitWriter.writeTransferCmdReq(gripperId, value);
    }

    private void setCompAck(int gripperId, boolean value) {
        bitWriter.writeTransferCompAck(gripperId, value);
    }
}
