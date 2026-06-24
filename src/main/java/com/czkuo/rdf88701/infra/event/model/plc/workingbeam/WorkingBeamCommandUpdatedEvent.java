package com.czkuo.rdf88701.infra.event.model.plc.workingbeam;

import com.czkuo.rdf88701.domain.plc.command.WorkingBeamCommand;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * WorkingBeamCommandUpdatedEvent
 * - 表示單一 Working Beam 的控制命令狀態更新事件
 * - 用於事件推播、記錄、或狀態同步流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class WorkingBeamCommandUpdatedEvent {

    /** WorkingBeam 裝置 ID */
    private final int workingBeamId;

    /** 最新命令狀態資料（完整快照） */
    private final WorkingBeamCommandStatus commandStatus;

    /** 安全取得內部 WorkingBeamCommand */
    private WorkingBeamCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得 TransferNo（命令流水號） */
    public int getTransferNo() {
        WorkingBeamCommand cmd = safeCmd();
        return cmd != null ? cmd.getTransferNo() : -1;
    }

    /** 取得命令類型字串（如 MOVE） */
    public String getCommandType() {
        WorkingBeamCommand cmd = safeCmd();
        return cmd != null ? cmd.getCommandType().getCommandName() : "UNKNOWN";
    }

    /** 取得動作方向（如 IN / OUT） */
    public String getDirection() {
        WorkingBeamCommand cmd = safeCmd();
        return cmd != null ? cmd.getCommandMeta().getDirectionDescription() : "UNKNOWN";
    }

    /** 判斷是否 Transfer Ready 狀態 */
    public boolean isTransferReady() {
        return commandStatus != null && commandStatus.isTransferReady();
    }
}
