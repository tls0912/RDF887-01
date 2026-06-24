package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Transfer 指令狀態批次推播訊息。
 *
 * <p>用於 `/topic/transfer/command/batch`，一次推送多台 Transfer 的 PLC 指令狀態
 * 快照。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Data
@Builder
public class TransferCommandBatchMessage {

    /**
     * 多筆 Transfer 控制狀態資料（對應各個 transferId 的 PLC Command 狀態）
     */
    private List<TransferCommandUpdatedMessage> commands;
}
