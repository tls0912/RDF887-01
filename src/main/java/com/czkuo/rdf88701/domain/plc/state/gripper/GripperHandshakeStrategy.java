package com.czkuo.rdf88701.domain.plc.state.gripper;

import com.czkuo.rdf88701.infra.entity.GripperTask;

/**
 * Gripper 握手策略接口
 * - 提供多種實作以支援不同類型 Gripper 裝置的交握流程
 */
public interface GripperHandshakeStrategy {

    /**
     * 推進 Gripper 任務握手流程
     *
     * @param task 當前任務
     * @param plcStatus 最新 PLC 回應狀態（read 區域）
     * @param cmdStatus 指令快取狀態（write 區域）
     */
    void tick(GripperTask task, GripperDeviceStatus plcStatus, GripperCommandStatus cmdStatus);
}