package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StrappingStatusCache
 * - 快取各 Strapping 裝置的最新狀態（來自 PLC Polling）
 * - 可由狀態機、監控器、UI 推播等模組查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class StrappingStatusCache {

    /**
     * Key 為 Strapping 名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態
     */
    private final Map<String, StrappingDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新狀態
     */
    public StrappingDeviceStatus getLatest(String strappingName) {
        return statusMap.get(strappingName);
    }

    /**
     * 更新對應 Strapping 的最新狀態
     */
    public void put(String strappingName, StrappingDeviceStatus status) {
        statusMap.put(strappingName, status);
    }

    /**
     * 判斷是否已存在快取資料
     */
    public boolean contains(String strappingName) {
        return statusMap.containsKey(strappingName);
    }

    /**
     * 清除所有快取（單元測試或重啟時使用）
     */
    public void clear() {
        statusMap.clear();
    }
}