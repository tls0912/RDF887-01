package com.czkuo.rdf88701.common.enums;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

public enum HandshakeReason {
    STARTUP,        // 開機
    HB_TIMEOUT,     // 心跳逾時後重連
    MANUAL,         // 手動觸發
    BROKER_RECOVER, // Broker復原
    OTHER
}
