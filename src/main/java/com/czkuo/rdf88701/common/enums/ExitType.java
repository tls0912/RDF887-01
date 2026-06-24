package com.czkuo.rdf88701.common.enums;

/**
 * 帳務離開方式（對應 location_flow.exit_type）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum ExitType {
    NORMAL,         // 正常出帳（任務完成）
    MANUAL,         // 人工出帳（操作人員手動移除）
    FORCE,          // 強制清帳（異常或強制清除）
    TIMEOUT,        // 超時自動清帳
    DISAPPEARED     // PLC 回報容器消失（如掉落、異常搬離）
}
