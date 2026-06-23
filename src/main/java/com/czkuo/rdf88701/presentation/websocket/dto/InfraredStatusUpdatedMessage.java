package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 紅外線設備狀態更新推播訊息
 * - 來源：InfraredStatusUpdatedEvent
 * - 用途：前端即時顯示紅外線測高設備狀態
 */
@Data
@Builder
public class InfraredStatusUpdatedMessage {

    /** 狀態快照時間 */
    private Instant timestamp;

    /** 紅外線設備 ID */
    private int infraredId;

    // === Bit 區位元 ===

    /** 是否待命（Standby） */
    private boolean infraredStandby;

    /** 是否收到命令確認（CMD Ack） */
    private boolean measureCmdAck;

    /** 是否完成測高請求（Completion Req） */
    private boolean measureCompReq;

    /** 是否發生告警（Alarm） */
    private boolean alarm;

    // === Word 區狀態 ===

    /** 主狀態碼（Raw Code） */
    private String deviceStatusCode;

    /** 主狀態描述（WorkingStatus / SubStatus） */
    private String deviceStatusDesc;

    /** 回傳結果碼（RetCode） */
    private String returnCode;

    /** 是否為過期資料（Stale） */
    private boolean stale;
}
