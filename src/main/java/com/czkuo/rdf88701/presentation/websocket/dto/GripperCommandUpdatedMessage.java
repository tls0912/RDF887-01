package com.czkuo.rdf88701.presentation.websocket.dto;

import com.czkuo.rdf88701.domain.plc.valueobject.GripperCommandType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Gripper 指令狀態更新推播訊息
 * - 對應 GripperCommand 資料內容
 * - 用於 WebSocket 將 PC → PLC 指令狀態推送給前端顯示
 */
@Data
@Builder
public class GripperCommandUpdatedMessage {

    private Instant timestamp;                       // 快照時間
    private int gripperId;                           // Gripper 裝置 ID

    private boolean gripperReady;                    // PC 已準備好（Ready Bit）
    private boolean gripperCmdReq;                   // 指令觸發請求（Cmd Req Bit）
    private boolean gripperCompAck;                  // 完成確認回應（Comp Ack Bit）

    private int gripperNo;                           // 指令流水號（W0100）
    private GripperCommandType taskType;             // 指令類型（W0101）

    private int bank;                                // 目標儲位 Bank（W0103）
    private int bay;                                 // 目標儲位 Bay（W0104）
    private int level;                               // 目標儲位 Level（W0105）

    private String productId;                        // 產品條碼（W0106-W011E）

    private boolean stale;                           // 是否過期（未更新超過閾值）
}
