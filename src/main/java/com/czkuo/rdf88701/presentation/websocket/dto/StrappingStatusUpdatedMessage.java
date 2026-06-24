package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;


/**
 * StrappingStatusUpdatedMessage
 * - 用於 WebSocket 推播單筆 Strapping 裝置狀態更新
 * - 對應 PLC Bit + Word 快照資料（讀取區）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class StrappingStatusUpdatedMessage {

    /** 快照時間 */
    private Instant timestamp;

    /** Strapping 裝置 ID */
    private int strappingId;

    // ======================== Bit 區狀態 ========================

    /** Strapping Standby（設備是否準備好）B0800 */
    private boolean strappingStandby;

    /** 指令確認位（CMD Ack）B0803 */
    private boolean strappingCmdAck;

    /** 指令完成請求位（Comp Req）B0804 */
    private boolean strappingCompReq;

    /** Alarm 異常警示位（B0807） */
    private boolean alarm;

    // ======================== Word 區欄位（可擴充） ========================

    /** 裝置主狀態碼（例：0x01 → IDLE） */
    private String deviceStatusCode;

    /** 裝置主狀態說明（例：IDLE、RUNNING、ERROR） */
    private String deviceStatusDesc;

    /** Return Code 回傳碼（例：0x0000 表示成功） */
    private String returnCode;

    /** Return Code 說明（例：Success、Timeout、Jam error） */
    private String returnCodeDesc;

    // ======================== 額外狀態 ========================

    /** 是否為過期快照資料（過久未更新） */
    private boolean stale;
}
