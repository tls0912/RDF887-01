package com.czkuo.rdf88701.presentation.websocket.dto;


import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * StrappingCommandBatchMessage
 * - 用於 WebSocket 推播多筆 Strapping 指令資料
 * - 通常應用於畫面初始化載入時使用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class StrappingCommandBatchMessage {

    /** Strapping 指令清單 */
    private List<StrappingCommandUpdatedMessage> commands;
}
