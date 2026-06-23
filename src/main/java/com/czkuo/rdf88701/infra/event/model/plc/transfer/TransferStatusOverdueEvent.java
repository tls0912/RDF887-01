package com.czkuo.rdf88701.infra.event.model.plc.transfer;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import lombok.Getter;
import lombok.ToString;

/**
 * Transfer 資料過期（Overdue）警告事件
 * - 當超過一定時間未更新時觸發
 * - 可用於警示系統、自動補救機制、或即時通知 UI
 */
@Getter
@ToString
public class TransferStatusOverdueEvent {

    /** Transfer 裝置 ID */
    private final int transferId;

    /** 過期時的最後一次設備快照 */
    private final TransferDeviceStatus deviceStatus;

    /** 最後一次更新時間（從 deviceStatus.snapshotTime 取出） */
    private final long lastUpdateTime;

    /** 觸發警告時的當前時間（系統時間） */
    private final long currentTime;

    /** 經過的時間差（毫秒） */
    private final long elapsedMillis;

    public TransferStatusOverdueEvent(int transferId, TransferDeviceStatus deviceStatus) {
        this.transferId = transferId;
        this.deviceStatus = deviceStatus;
        this.lastUpdateTime = deviceStatus != null && deviceStatus.getSnapshotTime() != null
                ? deviceStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}
