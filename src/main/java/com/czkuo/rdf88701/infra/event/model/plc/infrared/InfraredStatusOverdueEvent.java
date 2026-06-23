package com.czkuo.rdf88701.infra.event.model.plc.infrared;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import lombok.Getter;
import lombok.ToString;

/**
 * InfraredStatusOverdueEvent
 * - 紅外線測高裝置資料逾時事件
 * - 當設備超過設定時間未更新狀態時觸發
 * - 可用於系統警告、補償邏輯或通知 UI
 */
@Getter
@ToString
public class InfraredStatusOverdueEvent {

    /** 紅外線裝置 ID */
    private final int infraredId;

    /** 最後一次設備快照（逾時前的狀態） */
    private final InfraredDeviceStatus deviceStatus;

    /** 最後更新時間（來自 snapshotTime） */
    private final long lastUpdateTime;

    /** 系統當前時間（觸發事件時） */
    private final long currentTime;

    /** 經過時間（毫秒） */
    private final long elapsedMillis;

    public InfraredStatusOverdueEvent(int infraredId, InfraredDeviceStatus deviceStatus) {
        this.infraredId = infraredId;
        this.deviceStatus = deviceStatus;
        this.lastUpdateTime = deviceStatus != null && deviceStatus.getSnapshotTime() != null
                ? deviceStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}
