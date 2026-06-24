package com.czkuo.rdf88701.common.enums;

/**
 * Crane 任務狀態列舉
 * 對應資料表 crane_task.task_status 欄位
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum CraneTaskStatus {

    PENDING,        // 尚未下達
    DISPATCHED,     // 指令已送出，等待 PLC Ack
    IN_PROGRESS,    // 正在執行任務（含 FROM/TO 任一執行中）
    COMPLETED,      // 任務整體完成（含 FROM + TO 握手與搬運成功）
    FAILED,         // 任務失敗
    CANCELLED,      // 任務被取消
    SKIPPED,        // 被略過不執行
    RETRY           // 重新嘗試
}