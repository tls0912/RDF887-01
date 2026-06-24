package com.czkuo.rdf88701.domain.plc.command;

import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandMeta;
import com.czkuo.rdf88701.domain.plc.valueobject.WorkingBeamCommandType;
import lombok.Data;

/**
 * WorkingBeamCommand
 * - 封裝 PC → PLC Word 區控制命令內容（W0220 ~ W0225）
 * - 包含 Transfer No、Command Type、Direction、Location 三軸資訊
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
public class WorkingBeamCommand {

    private int transferNo;                        // 指令編號（W0220）
    private WorkingBeamCommandType commandType;    // 指令類型（W0221）
    private WorkingBeamCommandMeta commandMeta;    // 指令補充參數，如方向（W0222）

    public boolean isMoveCommand() {
        return commandType != null && commandType.isMove();
    }

    public boolean isInDirection() {
        return commandMeta != null && commandMeta.isIn();
    }

    public boolean isOutDirection() {
        return commandMeta != null && commandMeta.isOut();
    }

    public void cloneFrom(WorkingBeamCommand other) {
        if (other == null) return;
        this.transferNo = other.transferNo;
        this.commandType = other.commandType != null ? WorkingBeamCommandType.fromWord(other.commandType.toRaw()) : null;
        this.commandMeta = other.commandMeta != null ? WorkingBeamCommandMeta.fromWord(other.commandMeta.toRaw()) : null;
    }

    public static WorkingBeamCommand copyFrom(WorkingBeamCommand other) {
        if (other == null) return null;
        WorkingBeamCommand copy = new WorkingBeamCommand();
        copy.cloneFrom(other);
        return copy;
    }

    public boolean isDifferent(WorkingBeamCommand other) {
        if (other == null) return true;
        return this.transferNo != other.transferNo ||
                !safeEquals(this.commandType, other.commandType) ||
                !safeEquals(this.commandMeta, other.commandMeta);
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null) return b == null;
        return a.equals(b);
    }

    public String toSimpleString() {
        return String.format(
                "TNo=%d, CmdType=%s, Meta=%s",
                transferNo,
                commandType != null ? commandType.getCommandName() : "null",
                commandMeta != null ? commandMeta.getDirectionDescription() : "null"
        );
    }
}
