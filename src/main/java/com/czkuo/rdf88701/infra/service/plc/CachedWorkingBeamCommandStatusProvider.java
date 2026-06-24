package com.czkuo.rdf88701.infra.service.plc;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.service.plc.WorkingBeamCommandStatusProvider;
import com.czkuo.rdf88701.infra.cache.WorkingBeamCommandCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Working Beam 控制指令狀態提供者
 * - 根據 Working Beam ID 回傳合併狀態（read + last write）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Service
@RequiredArgsConstructor
public class CachedWorkingBeamCommandStatusProvider implements WorkingBeamCommandStatusProvider {

    private final WorkingBeamCommandCache cache;

    @Override
    public WorkingBeamCommandStatus getById(int workingBeamId) {
        return cache.getCombined(workingBeamId);
    }
}
