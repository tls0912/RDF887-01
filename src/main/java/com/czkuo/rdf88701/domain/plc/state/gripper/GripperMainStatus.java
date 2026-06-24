package com.czkuo.rdf88701.domain.plc.state.gripper;

import lombok.Getter;

/**
 * Gripper 主流程狀態
 * - 對應設備大致上的流程進度
 * - 供 StateMachine 和事件系統使用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
public enum GripperMainStatus {

    /** 待機中（沒有命令） */
    IDLE,

    /** 已準備好接受命令 */
    READY,

    /** 正在執行命令 */
    RUNNING,

    /** 任務完成（等待歸零或進下一輪） */
    COMPLETED,

    /** 異常（Alarm或故障） */
    ERROR
}
