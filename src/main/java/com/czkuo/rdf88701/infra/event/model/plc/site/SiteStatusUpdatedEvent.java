package com.czkuo.rdf88701.infra.event.model.plc.site;

import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * SiteStatusUpdatedEvent
 * - 表示單一 Site 裝置的設備狀態更新事件
 * - 用於事件推播、狀態記錄、或狀態同步流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SiteStatusUpdatedEvent {

    /** Site 裝置 ID */
    private final int siteId;

    /** 最新設備狀態資料（完整快照） */
    private final SiteDeviceStatus deviceStatus;
}
