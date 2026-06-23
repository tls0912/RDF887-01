package com.czkuo.rdf88701.infra.event.model.plc.infrared;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import lombok.Getter;
import lombok.ToString;

/**
 * InfraredCommandOverdueEvent
 * - 紅外線測高指令逾時事件（未完成或無 CompReq）
 * - 當指令下達後 PLC 未在預期時間內完成（CompReq 未出現）時觸發
 * - 可用於異常警示、補償邏輯或 UI 提示
 */
@Getter
@ToString
public class InfraredCommandOverdueEvent {

    /** 紅外線裝置 ID */
    private final int infraredId;

    /** 指令逾時時的設備快照 */
    private final InfraredCommandStatus commandStatus;

    /** 最後更新時間（來源為 snapshotTime） */
    private final long lastUpdateTime;

    /** 當前時間（觸發事件的系統時間） */
    private final long currentTime;

    /** 經過的時間差（毫秒） */
    private final long elapsedMillis;

    public InfraredCommandOverdueEvent(int infraredId, InfraredCommandStatus commandStatus) {
        this.infraredId = infraredId;
        this.commandStatus = commandStatus;
        this.lastUpdateTime = commandStatus != null && commandStatus.getSnapshotTime() != null
                ? commandStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}
