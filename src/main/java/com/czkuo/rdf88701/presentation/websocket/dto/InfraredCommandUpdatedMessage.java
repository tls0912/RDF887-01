package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 紅外線設備指令狀態更新推播訊息
 * - 來源：InfraredCommandUpdatedEvent
 * - 用途：前端即時顯示紅外線測高設備控制指令狀態
 */
@Data
@Builder
public class InfraredCommandUpdatedMessage {

    /** 狀態快照時間 */
    private Instant timestamp;

    /** 紅外線設備 ID */
    private int infraredId;

    // === Bit 區位元 ===

    /** 是否 Ready（設備準備好接受指令） */
    private boolean infraredReady;

    /** 是否已發出測高命令（CMD Request） */
    private boolean measureCmdReq;

    /** 是否已完成並回應（Completion ACK） */
    private boolean measureCompAck;

    // === Word 區指令欄位 ===

    /** 指令編號（流水號） */
    private int infraredNo;

    /** 產品厚度 */
    private int trayThickness;

    /** 是否為過期資料 */
    private boolean stale;
}
