package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * WorkingBeam 指令狀態批次推播訊息
 * <p>
 * 用於 WebSocket 推送多筆 WorkingBeam 控制指令快照。
 */
@Data
@Builder
public class WorkingBeamCommandBatchMessage {

    /**
     * 多筆 WorkingBeam 控制狀態資料（對應各個 workingBeamId 的 PLC Command 狀態）
     */
    private List<WorkingBeamCommandUpdatedMessage> commands;
}
