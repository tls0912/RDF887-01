package com.czkuo.rdf88701.infra.event.model.plc.safety;

import com.czkuo.rdf88701.domain.plc.state.safety.SafetyDeviceStatus;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Safety 資料過期（Overdue）警告事件
 * - 當某安全設備群組（Bank）超過一定時間未更新時觸發
 * - 可用於警示系統、自動補救機制、或即時通知 UI
 */
@Getter
@ToString
public class SafetyStatusOverdueEvent {

    /** 安全設備群組（Bank）ID */
    private final int deviceId;

    /** 過期時的最後一次安全快照（可為 null；視建構子而定） */
    private final SafetyDeviceStatus deviceStatus;

    /** 最後一次更新時間（epoch millis；若未知為 -1） */
    private final long lastSnapshotTime;

    /** 觸發警告時的當前時間（epoch millis） */
    private final long currentTime;

    /** 經過的時間差（毫秒；若未知為 -1） */
    private final long elapsedMillis;

    /**
     * 以完整的 SafetyDeviceStatus 建構（含快照內容）
     */
    public SafetyStatusOverdueEvent(int deviceId, SafetyDeviceStatus deviceStatus) {
        this.deviceId = deviceId;
        this.deviceStatus = deviceStatus;
        this.lastSnapshotTime = (deviceStatus != null && deviceStatus.getSnapshotTime() != null)
                ? deviceStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastSnapshotTime > 0) ? (currentTime - lastSnapshotTime) : -1L;
    }

    /**
     * 僅以最後快照時間建構（無快照內容）
     * 方便 SafetyPollingHandler 直接用 snapshotTime 觸發事件
     */
    public SafetyStatusOverdueEvent(int deviceId, Instant lastSnapshot) {
        this.deviceId = deviceId;
        this.deviceStatus = null;
        this.lastSnapshotTime = (lastSnapshot != null) ? lastSnapshot.toEpochMilli() : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastSnapshotTime > 0) ? (currentTime - lastSnapshotTime) : -1L;
    }
}
