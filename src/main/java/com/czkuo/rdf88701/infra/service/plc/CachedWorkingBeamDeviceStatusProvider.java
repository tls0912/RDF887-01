package com.czkuo.rdf88701.infra.service.plc;

import com.czkuo.rdf88701.config.plc.PlcWorkingBeamRegistry;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.service.plc.WorkingBeamDeviceStatusProvider;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 從快取中取得 PLC 回報的 Working Beam 狀態
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class CachedWorkingBeamDeviceStatusProvider implements WorkingBeamDeviceStatusProvider {

    private final WorkingBeamStatusCache statusCache;
    private final PlcWorkingBeamRegistry beamRegistry;

    @Override
    public WorkingBeamDeviceStatus getById(Long workingBeamId) {
        String name = beamRegistry.getWorkingBeamNameById(workingBeamId.intValue());
        return statusCache.getLatest(name);
    }
}
