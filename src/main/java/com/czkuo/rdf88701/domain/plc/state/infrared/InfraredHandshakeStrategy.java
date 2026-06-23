package com.czkuo.rdf88701.domain.plc.state.infrared;

import com.czkuo.rdf88701.infra.entity.InfraredTask;

/**
 * Infrared 握手策略接口
 * - 提供多種實作以支援不同類型 Infrared 裝置的交握行為
 */
public interface InfraredHandshakeStrategy {

    /**
     * 推進 Infrared 任務握手流程
     *
     * @param task        當前任務
     * @param plcStatus   最新 PLC 回應狀態（read 區域）
     * @param cmdStatus   指令快取狀態（write 區域）
     */
    void tick(InfraredTask task, InfraredDeviceStatus plcStatus, InfraredCommandStatus cmdStatus);
}
