package com.czkuo.rdf88701.infra.event.model.plc.crane;

import com.czkuo.rdf88701.domain.plc.command.CraneCommand;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * CraneCommandUpdatedEvent
 * - 表示單一 Crane 的控制命令狀態更新事件
 * - 用於事件推播、記錄、或狀態同步流程
 */
@Getter
@ToString
@RequiredArgsConstructor
public class CraneCommandUpdatedEvent {

    /** Crane 編號 */
    private final int craneId;

    /** 最新命令狀態資料（完整快照） */
    private final CraneCommandStatus commandStatus;

    /** 安全取得內部 CraneCommand */
    private CraneCommand safeCmd() {
        return commandStatus != null ? commandStatus.getCommand() : null;
    }

    /** 取得來源 CST ID */
    public String getFromCstId() {
        CraneCommand cmd = safeCmd();
        return cmd != null ? cmd.getFromCstId() : null;
    }

    /** 取得目標 CST ID */
    public String getToCstId() {
        CraneCommand cmd = safeCmd();
        return cmd != null ? cmd.getToCstId() : null;
    }

    /** 取得來源位置（格式化字串） */
    public String getFromLocation() {
        CraneCommand cmd = safeCmd();
        if (cmd == null) return "-";
        return String.format("Bank:%d, Bay:%d, Level:%d",
                cmd.getFromLocationBank(),
                cmd.getFromLocationBay(),
                cmd.getFromLocationLv());
    }

    /** 取得目標位置（格式化字串） */
    public String getToLocation() {
        CraneCommand cmd = safeCmd();
        if (cmd == null) return "-";
        return String.format("Bank:%d, Bay:%d, Level:%d",
                cmd.getToLocationBank(),
                cmd.getToLocationBay(),
                cmd.getToLocationLv());
    }

    /** 取得是否為 Transfer Ready */
    public boolean isTransferReady() {
        return commandStatus != null && commandStatus.isTransferReady();
    }
}
