package com.czkuo.rdf88701.presentation.websocket.dto;

import com.czkuo.rdf88701.domain.plc.valueobject.TransferCommandType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Transfer 指令狀態更新推播訊息
 * - 對應 TransferCommand 資料內容
 * - 用於 WebSocket 將 PC → PLC 指令狀態推送給前端顯示
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class TransferCommandUpdatedMessage {

    private Instant timestamp;                        // 快照時間
    private int transferId;                           // Transfer 裝置 ID

    private boolean transferReady;                    // PC 已準備好（Ready Bit）
    private boolean transferCmdReq;                   // 指令觸發請求（Cmd Req Bit）
    private boolean transferCompAck;                  // 完成確認回應（Comp Ack Bit）

    private int transferNo;                           // 指令流水號（W0100）
    private TransferCommandType commandType;          // 指令類型（W0101）

    private int locationBank;                         // 目標儲位 Bank（W0103）
    private int locationBay;                          // 目標儲位 Bay（W0104）
    private int locationLevel;                        // 目標儲位 Level（W0105）

    private String productId;                         // 產品條碼（W0106-W011E）

    private boolean stale;                            // 是否過期（未更新超過閾值）
}
