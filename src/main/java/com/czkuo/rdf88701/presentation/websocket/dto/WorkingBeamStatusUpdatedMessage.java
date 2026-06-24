package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * WorkingBeam 狀態更新推播訊息
 * <p>
 * 用於 WebSocket 推送單筆 Working Beam 狀態更新資料。
 * 結構參考 Crane 設計，支援即時監控。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class WorkingBeamStatusUpdatedMessage {

    private Instant timestamp;              // 快照時間
    private int workingBeamId;              // Working Beam 裝置 ID
    private String currentState;            // 狀態機當前狀態名稱（例如：IDLE, MOVING, HOLDING 等）

    private boolean transferStandby;        // 是否 Ready 處於可接收狀態
    private boolean transferCmdAck;         // 傳送指令確認 Ack
    private boolean transferCompReq;        // 傳送完成要求
    private boolean alarm;                  // 是否有警報

    private String deviceStatusCode;        // 裝置狀態碼（字串格式，如 0x01）
    private String returnCode;              // 回應碼（字串格式，如 0x00）

    private boolean stale;                  // 是否資料過期（即長時間未更新）
}
