package com.czkuo.rdf88701.infra.event.model.plc.gripper;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Gripper 指令資料過期（Command Overdue）警告事件
 * - 當 PLC 指令區長時間未更新時觸發
 * - 可用於監控是否有指令卡住、異常未清除等情況
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
public class GripperCommandOverdueEvent {

    /** Gripper 裝置 ID */
    private final int gripperId;

    /** 過期時的最後一次指令快照 */
    private final GripperCommandStatus commandStatus;

    /** 最後一次更新時間（從 commandStatus.snapshotTime 取出） */
    private final long lastUpdateTime;

    /** 觸發警告時的當前時間（系統時間） */
    private final long currentTime;

    /** 經過的時間差（毫秒） */
    private final long elapsedMillis;

    public GripperCommandOverdueEvent(int gripperId, GripperCommandStatus commandStatus) {
        this.gripperId = gripperId;
        this.commandStatus = commandStatus;
        this.lastUpdateTime = commandStatus != null && commandStatus.getSnapshotTime() != null
                ? commandStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}
