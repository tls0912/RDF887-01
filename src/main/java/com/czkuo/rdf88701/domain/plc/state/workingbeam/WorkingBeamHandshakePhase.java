package com.czkuo.rdf88701.domain.plc.state.workingbeam;

/**
 * 定義 Working Beam 任務握手階段
 * 適用於單段式的交握控制狀態（無 FROM/TO 區分）
 */
public enum WorkingBeamHandshakePhase {

    NONE,                  // 尚未啟動（預設狀態）

    CMD_SENT,              // 指令已發送（寫入 Word 與觸發 Req Bit）
    ACK_RECEIVED,          // PLC 回應 Ack（PLC CmdAck Bit 已開）
    CMD_REQ_CLEARED,       // PC 清除 Req Bit，完成 Ack 回應階段

    IN_PROGRESS,           // PLC JobHandling 中（正在執行動作）

    COMPLETION_RECEIVED,   // PLC 通知命令完成（CompReq Bit 開）
    RESPONDED_COMPLETION,  // PC 已回覆完成（CompAck Bit 開）
    COMPLETION_CONFIRMED,  // PLC 關閉 CompReq，PC 清除 CompAck

    DONE,                  // 全部握手完成（狀態機結束）

    FAILED                 // 發生錯誤（如逾時、錯誤回碼、PLC 無應答等）
}
