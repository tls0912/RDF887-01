package com.czkuo.rdf88701.infra.event.model.plc.site;

import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * SiteCommandUpdatedEvent
 * - 表示單一 Site 的控制命令狀態更新事件
 * - 用於事件推播、記錄、或狀態同步流程
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SiteCommandUpdatedEvent {

    /** Site 裝置 ID */
    private final int siteId;

    /** 最新命令狀態資料（完整快照） */
    private final SiteCommandStatus commandStatus;

    /** 判斷是否 Site Ready 狀態 */
    public boolean isSiteReady() {
        return commandStatus != null && commandStatus.isSiteReady();
    }
}
