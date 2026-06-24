package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 紅外線設備狀態批次更新推播訊息
 * - 用於批次推播紅外線設備的狀態更新
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class InfraredStatusBatchMessage {

    /** 紅外線設備狀態更新訊息列表 */
    private List<InfraredStatusUpdatedMessage> infrareds;
}
