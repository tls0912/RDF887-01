package com.czkuo.rdf88701.infra.event.model.plc.site;

import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import lombok.Getter;
import lombok.ToString;

/**
 * SiteCommandOverdueEvent
 * - Site 指令資料過期（Command Overdue）警告事件
 * - 當 PLC 指令區長時間未更新時觸發
 * - 可用於監控是否有指令卡住、異常未清除等情況
 */
@Getter
@ToString
public class SiteCommandOverdueEvent {

    /** Site 裝置 ID */
    private final int siteId;

    /** 過期時的最後一次指令快照 */
    private final SiteCommandStatus commandStatus;

    /** 最後一次更新時間（從 commandStatus.snapshotTime 取出） */
    private final long lastUpdateTime;

    /** 觸發警告時的當前時間（系統時間） */
    private final long currentTime;

    /** 經過的時間差（毫秒） */
    private final long elapsedMillis;

    public SiteCommandOverdueEvent(int siteId, SiteCommandStatus commandStatus) {
        this.siteId = siteId;
        this.commandStatus = commandStatus;
        this.lastUpdateTime = commandStatus != null && commandStatus.getSnapshotTime() != null
                ? commandStatus.getSnapshotTime().toEpochMilli()
                : -1L;
        this.currentTime = System.currentTimeMillis();
        this.elapsedMillis = (lastUpdateTime > 0) ? (currentTime - lastUpdateTime) : -1L;
    }
}