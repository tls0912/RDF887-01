package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Site 狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆 Site 狀態更新資料。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class SiteStatusBatchMessage {

    /** 多筆 Site 狀態資料 */
    private List<SiteStatusUpdatedMessage> sites;
}
