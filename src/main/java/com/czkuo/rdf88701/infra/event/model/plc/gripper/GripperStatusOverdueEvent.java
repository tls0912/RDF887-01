package com.czkuo.rdf88701.infra.event.model.plc.gripper;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import lombok.Getter;
import lombok.ToString;

/**
 * Gripper 資料過期（Overdue）警告事件
 * - 當超過一定時間未更新時觸發
 * - 可用於警示系統、自動補救機制、或即時通知 UI
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
public class GripperStatusOverdueEvent {

    /** Gripper 編號 */
    private final int gripperId;

    /** 過期時的最後一次設備快照 */
    private final GripperDeviceStatus deviceStatus;

    /** 最後一次更新時間（從 deviceStatus.snapshotTime 取出） */
    private final long lastUpdateTime;

    /** 觸發警告時的當前時間（系統時間） */
    private final long currentTime;

    /** 經過的時間差（毫秒） */
    private final long elapsedMillis;

    public GripperStatusOverdueEvent(int gripperId, GripperDeviceStatus deviceStatus) {
        this.gripperId = gripperId;
        this.deviceStatus = deviceStatus;
        this.lastUpdateTime = deviceStatus != null && deviceStatus.getSnapshotTime() != null
                ? deviceStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}
