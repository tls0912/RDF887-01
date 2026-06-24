package com.czkuo.rdf88701.presentation.websocket.dto;

import com.czkuo.rdf88701.domain.plc.valueobject.StrappingCommandMode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * StrappingCommandUpdatedMessage
 * - 用於 WebSocket 推播單筆 Strapping 指令更新
 * - 對應 PLC Bit + Word 快照資料
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class StrappingCommandUpdatedMessage {

    /** 快照時間 */
    private Instant timestamp;

    /** Strapping 裝置 ID */
    private int strappingId;

    // ======================== Bit 區狀態 ========================

    /** Strapping Ready（設備是否準備好） */
    private boolean strappingReady;

    /** 指令請求位（CMD Req） */
    private boolean strappingCmdReq;

    /** 指令完成確認位（Comp Ack） */
    private boolean strappingCompAck;

    // ======================== Word 區欄位 ========================

    /** 綁帶任務編號（W0398） */
    private int strappingNo;

    /** 綁帶次數（W039A） */
    private int strappingCount;

    /** 綁帶執行模式（W039B） */
    private String strappingMode;

    // ======================== 額外狀態 ========================

    /** 是否為過期快照資料（過久未更新） */
    private boolean stale;
}
