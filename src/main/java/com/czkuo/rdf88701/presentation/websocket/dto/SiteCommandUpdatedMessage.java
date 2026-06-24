package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Site 指令狀態更新推播訊息
 * <p>
 * 用於呈現 PC 寫入至 PLC 的控制命令內容。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class SiteCommandUpdatedMessage {

    private Instant timestamp;                        // 快照時間
    private int siteId;                               // Site 裝置 ID

    private boolean siteReady;                        // PC 已準備好（Ready Bit）
    private boolean removeAccountAck;                 // 完成確認回應（Cmd Req Bit）
    private boolean portReportPc;

    private String productId;                         // 產品條碼

    private boolean stale;                            // 是否過期（未更新超過閾值）
}
