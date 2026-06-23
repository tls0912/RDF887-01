package com.czkuo.rdf88701.presentation.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 批次推播：Gripper 最新狀態列表
 */
@Getter
@Builder
@AllArgsConstructor
public class GripperStatusBatchMessage {
    private final List<GripperStatusUpdatedMessage> grippers;
}
