package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorkingBeamStatusCache
 * - 快取各 Working Beam 裝置的最新狀態（來自 PLC Polling）
 * - 可由狀態機、監控器、UI 推播等模組查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class WorkingBeamStatusCache {

    /**
     * Key 為 WorkingBeam 名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態
     */
    private final Map<String, WorkingBeamDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新狀態
     */
    public WorkingBeamDeviceStatus getLatest(String beamName) {
        return statusMap.get(beamName);
    }

    /**
     * 更新對應 Working Beam 的最新狀態
     */
    public void put(String beamName, WorkingBeamDeviceStatus status) {
        statusMap.put(beamName, status);
    }

    /**
     * 判斷是否已存在快取資料
     */
    public boolean contains(String beamName) {
        return statusMap.containsKey(beamName);
    }

    /**
     * 清除所有快取（單元測試或重啟時使用）
     */
    public void clear() {
        statusMap.clear();
    }
}
