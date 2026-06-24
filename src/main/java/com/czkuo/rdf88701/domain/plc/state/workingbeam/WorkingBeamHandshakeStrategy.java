package com.czkuo.rdf88701.domain.plc.state.workingbeam;

import com.czkuo.rdf88701.infra.entity.WorkingBeamTask;

/**
 * Working Beam 握手策略接口
 * - 提供多種實作以支援不同類型 WorkingBeam 的交握行為
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface WorkingBeamHandshakeStrategy {

    /**
     * 推進 WorkingBeam 任務握手流程
     *
     * @param task 當前任務
     * @param plcStatus 最新 PLC 回應狀態（read區域）
     * @param cmdStatus 指令快取狀態（write區域）
     */
    void tick(WorkingBeamTask task, WorkingBeamDeviceStatus plcStatus, WorkingBeamCommandStatus cmdStatus);
}
