package com.czkuo.rdf88701.domain.service.plc;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;

/**
 * 提供 Working Beam 的寫入命令狀態快取（由 PC 控制端所記錄）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
public interface WorkingBeamCommandStatusProvider {

    /**
     * 根據 Working Beam ID 查詢最近一次寫入命令狀態（由 PC 自行記錄）
     *
     * @param workingBeamId Working Beam 裝置 ID
     * @return 控制指令狀態（快取）
     */
    WorkingBeamCommandStatus getById(int workingBeamId);
}
