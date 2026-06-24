package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Site 狀態更新推播訊息
 * <p>
 * 用於 WebSocket 推送單筆 Site 狀態更新資料。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class SiteStatusUpdatedMessage {

    private Instant timestamp;         // 快照時間
    private int siteId;                // Site 裝置 ID

    private boolean siteStandby;       // Site 是否處於可接收狀態（例：等待 Transfer）
    private boolean productPresent;    // 有產品存在於 Site 上
    private boolean removeAccountReq;  // 請求刪帳（通常代表產品離站）
    private boolean portReportPlc;     // 進出站請求（需由系統判斷是 IN 或 OUT）

    private String productId;          // 產品 ID（如有）

    private boolean stale;             // 是否資料過期（長時間未更新）
}
