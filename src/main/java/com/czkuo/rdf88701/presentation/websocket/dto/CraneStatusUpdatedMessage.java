package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Crane 狀態更新推播訊息
 * <p>
 * 用於 WebSocket 推送單筆天車狀態更新
 */
@Data
@Builder
public class CraneStatusUpdatedMessage {

    private Instant timestamp;            // 快照時間
    private int craneId;                  // 天車 ID
    private String stateMachineState;     // 狀態機當前狀態名稱（例如：IDLE, MOVING, BUSY 等）

    private boolean transferStandby;      // 是否 Transfer Standby 中
    private boolean cstPresent;           // CST 是否在位
    private boolean readyHandleFromCmd;   // 是否可處理 From 指令
    private boolean readyHandleToCmd;     // 是否可處理 To 指令
    private boolean fromJobHandling;      // From 任務處理中
    private boolean fromTransferCmdAck;   // From Transfer CMD Ack
    private boolean fromTransferCompReq;  // From Transfer 完成要求
    private boolean toJobHandling;        // To 任務處理中
    private boolean toTransferCmdAck;     // To Transfer CMD Ack
    private boolean toTransferCompReq;    // To Transfer 完成要求
    private boolean homeReturnAck;        // 原點回歸確認
    private boolean removeAccountReq;     // Remove Account 要求

    private int bayPosition;              // Bay 位置
    private int levelPosition;            // Level 位置
    private int bankPosition;             // Bank 位置
    private String deviceStatusCode;      // 狀態碼（字串格式，例如 0x01）
    private String fromReturnCode;        // From 回應碼
    private String toReturnCode;          // To 回應碼
    private String productId;             // 產品 ID（如有）

    private boolean stale;                // 是否過期（超過指定秒數未更新）
}
