package com.czkuo.rdf88701.common.enums;

/**
 * Transfer 任務狀態列舉
 * 對應資料表 transfer_task.task_status 欄位
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum TransferTaskStatus {

    PENDING,        // 尚未下達（等待排程派送）
    DISPATCHED,     // 指令已送出，等待 PLC Ack
    IN_PROGRESS,    // 正在執行任務
    COMPLETED,      // 任務完成
    FAILED,         // 任務失敗
    CANCELLED,      // 任務被取消
    SKIPPED,        // 被略過不執行（保留用）
    RETRY           // 重新嘗試執行
}
