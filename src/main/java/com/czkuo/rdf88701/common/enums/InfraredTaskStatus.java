package com.czkuo.rdf88701.common.enums;

/**
 * Infrared 任務狀態列舉
 * 對應資料表 infrared_task.task_status 欄位
 */
public enum InfraredTaskStatus {

    PENDING,        // 尚未下達
    DISPATCHED,     // 指令已送出，等待 PLC Ack
    IN_PROGRESS,    // 正在執行任務
    COMPLETED,      // 任務完成
    FAILED,         // 任務失敗
    CANCELLED,      // 任務被取消
    SKIPPED,        // 被略過不執行（保留，視情況使用）
    RETRY           // 重新嘗試執行
}
