package com.czkuo.rdf88701.infra.event.model.plc.transfer;

import com.czkuo.rdf88701.domain.plc.command.TransferCommand;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * TransferCommandUpdatedEvent
 * - 表示單一 Transfer 的控制命令狀態更新事件
 * - 用於事件推播、記錄、或狀態同步流程
 */
@Getter
@ToString
@RequiredArgsConstructor
public class TransferCommandUpdatedEvent {

    /** Transfer 裝置 ID */
    private final int transferId;

    /** 最新命令狀態資料（完整快照） */
    private final TransferCommandStatus commandStatus;

    /** 安全取得內部 TransferCommand */
    private TransferCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得 TransferNo（命令流水號） */
    public int getTransferNo() {
        TransferCommand cmd = safeCmd();
        return cmd != null ? cmd.getTransferNo() : -1;
    }

    /** 取得命令類型字串（如 MOVE） */
    public String getCommandType() {
        TransferCommand cmd = safeCmd();
        return cmd != null ? cmd.getTaskType().getCommandName() : "UNKNOWN";
    }

    /** 判斷是否 Transfer Ready 狀態 */
    public boolean isTransferReady() {
        return commandStatus != null && commandStatus.isTransferReady();
    }
}
