package com.czkuo.rdf88701.domain.plc.state.infrared;

/**
 * 定義 Infrared 任務握手階段
 * 適用於單段式的交握控制狀態
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public enum InfraredHandshakePhase {

    NONE,                   // 尚未啟動（預設狀態）

    CMD_SENT,               // 指令已發送（寫入 Word 與觸發 CmdReq Bit）
    ACK_RECEIVED,           // PLC 回應 Ack（PLC CmdAck Bit 已開）
    CMD_REQ_CLEARED,        // PC 清除 CmdReq Bit，完成 Ack 回應階段

    IN_PROGRESS,            // PLC JobHandling 中（正在執行測量）

    COMPLETION_RECEIVED,    // PLC 通知命令完成（CompReq Bit 開）
    RESPONDED_COMPLETION,   // PC 已回覆完成（CompAck Bit 開）
    COMPLETION_CONFIRMED,   // PLC 關閉 CompReq，PC 清除 CompAck

    DONE,                   // 全部握手完成（狀態機結束）

    FAILED                  // 發生錯誤（如逾時、錯誤回碼、PLC 無應答等）
}
