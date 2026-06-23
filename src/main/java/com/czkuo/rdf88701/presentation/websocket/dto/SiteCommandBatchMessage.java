package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Site 指令狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆 Site 控制指令快照。
 */
@Data
@Builder
public class SiteCommandBatchMessage {

    /**
     * 多筆 Site 控制狀態資料（對應各個 siteId 的 PLC Command 狀態）
     */
    private List<SiteCommandUpdatedMessage> commands;
}
