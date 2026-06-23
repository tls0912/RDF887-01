package com.czkuo.rdf88701.domain.service.plc;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;

/**
 * 提供 Working Beam 的 PLC 裝置狀態（來源為 Polling 快取）
 */
public interface WorkingBeamDeviceStatusProvider {

    /**
     * 根據 Working Beam ID 取得最新 PLC 裝置狀態
     *
     * @param workingBeamId Working Beam 資料庫 ID
     * @return 當前裝置狀態（快取中資料）
     */
    WorkingBeamDeviceStatus getById(Long workingBeamId);
}
