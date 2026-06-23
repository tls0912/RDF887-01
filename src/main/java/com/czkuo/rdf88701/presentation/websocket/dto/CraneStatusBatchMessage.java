package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Crane 狀態更新批次推播訊息
 */
@Data
@Builder
public class CraneStatusBatchMessage {
    private List<CraneStatusUpdatedMessage> cranes;
}
