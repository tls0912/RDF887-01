package com.czkuo.rdf88701.domain.plc.state.transfer;

import com.czkuo.rdf88701.infra.entity.TransferTask;

/**
 * Transfer 握手策略接口
 * - 提供多種實作以支援不同類型 Transfer 裝置的交握流程
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface TransferHandshakeStrategy {

    /**
     * 推進 Transfer 任務握手流程
     *
     * @param task 當前任務
     * @param plcStatus 最新 PLC 回應狀態（read 區域）
     * @param cmdStatus 指令快取狀態（write 區域）
     */
    void tick(TransferTask task, TransferDeviceStatus plcStatus, TransferCommandStatus cmdStatus);
}
