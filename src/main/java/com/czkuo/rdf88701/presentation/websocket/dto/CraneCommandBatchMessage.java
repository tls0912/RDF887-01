package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Crane 指令狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆天車控制指令快照。
 */
@Data
@Builder
public class CraneCommandBatchMessage {

    /**
     * 多筆天車控制狀態資料（對應各個 craneId 的 PLC Command 狀態）
     */
    private List<CraneCommandUpdatedMessage> commands;
}
