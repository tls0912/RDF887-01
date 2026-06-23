package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Transfer 狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆 Transfer 狀態更新資料。
 */
@Data
@Builder
public class TransferStatusBatchMessage {

    /** 多筆 Transfer 狀態資料 */
    private List<TransferStatusUpdatedMessage> transfers;
}
