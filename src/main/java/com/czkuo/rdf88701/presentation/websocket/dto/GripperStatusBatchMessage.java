package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 批次推播：Gripper 最新狀態列表
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Getter
@Builder
@AllArgsConstructor
public class GripperStatusBatchMessage {
    private final List<GripperStatusUpdatedMessage> grippers;
}
