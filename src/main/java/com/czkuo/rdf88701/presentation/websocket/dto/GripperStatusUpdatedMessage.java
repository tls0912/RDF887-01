package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * WebSocket 傳送到前端的 Gripper 狀態更新資料
 * - 精確展開每個欄位
 * - 對應後端推播內容，前端直接使用
 */
@Getter
@Builder
public class GripperStatusUpdatedMessage {

    private final String event = "gripper_status_updated"; // 固定事件名稱
    private final Instant timestamp;                      // 資料產生時間
    private final int gripperId;                           // Gripper編號

    private final String stateMachineState;                // 狀態機主狀態 (IDLE / RUNNING / etc)
    private final String runningSubStatus;                 // 細部執行子狀態代碼 (MOVING / PICKING)
    private final String runningSubStatusText;             // 細部執行子狀態中文說明 (移動中 / 夾取中)

    // ====== 以下是 DeviceStatus 展開的欄位 ======
    private final boolean ready;             // 是否 Ready
    private final boolean productPresent;    // 是否有產品
    private final boolean transferCmdAck;    // 是否 Command Ack
    private final boolean transferCompReq;   // 是否 Completion Req
    private final boolean alarm;             // 是否 Alarm
    private final int bay;                   // Bay位置
    private final int level;                 // Level位置
    private final int bank;                  // Bank位置
    private final String deviceStatusCode;   // 裝置狀態碼 (格式化成 0xXX)
    private final String returnCode;         // 完成回傳碼 (格式化成 0xXX)
    private final String productId;          // 產品ID
    private final boolean stale;             // 是否過期
}
