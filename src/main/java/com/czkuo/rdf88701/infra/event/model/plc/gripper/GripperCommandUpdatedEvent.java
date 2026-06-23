package com.czkuo.rdf88701.infra.event.model.plc.gripper;

import com.czkuo.rdf88701.domain.plc.command.GripperCommand;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * GripperCommandUpdatedEvent
 * - 表示單一 Gripper 裝置的指令控制狀態已更新
 * - 用於事件推播、日誌紀錄或狀態同步
 */
@Getter
@ToString
@RequiredArgsConstructor
public class GripperCommandUpdatedEvent {

    /** Gripper 裝置 ID */
    private final int gripperId;

    /** 最新的命令狀態快照（Bit + Word 合併） */
    private final GripperCommandStatus commandStatus;

    /** 安全取得內部 GripperCommand */
    private GripperCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得 Transfer No（指令編號） */
    public int getTransferNo() {
        GripperCommand cmd = safeCmd();
        return cmd != null ? cmd.getTransferNo() : -1;
    }

    /** 取得命令類型字串（如 MOVE / PICK / DROP） */
    public String getCommandType() {
        GripperCommand cmd = safeCmd();
        return cmd != null ? cmd.getTaskType().getCommandName() : "UNKNOWN";
    }

    /** 判斷是否處於 Gripper Ready 狀態 */
    public boolean isGripperReady() {
        return commandStatus != null && commandStatus.isTransferReady();
    }
}
