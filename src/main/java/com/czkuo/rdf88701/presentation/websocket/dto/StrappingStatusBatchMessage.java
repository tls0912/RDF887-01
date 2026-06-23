package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * StrappingStatusBatchMessage
 * - 用於 WebSocket 推播多筆 Strapping 裝置狀態資料
 * - 通常應用於初始化畫面載入或批次同步時
 */
@Data
@Builder
public class StrappingStatusBatchMessage {

    /** 狀態清單 */
    private List<StrappingStatusUpdatedMessage> strappings;
}
