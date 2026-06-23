package com.czkuo.rdf88701.domain.plc.state.crane;

import com.czkuo.rdf88701.infra.entity.CraneTask;

/**
 * Crane 握手策略接口
 * - 提供多種實作以支援不同類型 Crane 握手行為
 */
public interface CraneHandshakeStrategy {

    /**
     * 推進任務握手狀態
     *
     * @param task 當前任務
     * @param plcStatus 最新 PLC 回應狀態（read區域）
     * @param cmdStatus 指令快取狀態（write區域）
     */
    void tick(CraneTask task, CraneDeviceStatus plcStatus, CraneCommandStatus cmdStatus);
}
