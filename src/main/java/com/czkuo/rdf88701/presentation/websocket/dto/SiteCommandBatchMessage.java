package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Site 指令狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆 Site 控制指令快照。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class SiteCommandBatchMessage {

    /**
     * 多筆 Site 控制狀態資料（對應各個 siteId 的 PLC Command 狀態）
     */
    private List<SiteCommandUpdatedMessage> commands;
}
