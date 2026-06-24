package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InfraredStatusCache
 * - 快取各紅外線設備的最新狀態（由 PLC Polling 得到）
 * - 提供給狀態機、監控服務、UI 推播等模組查詢使用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class InfraredStatusCache {

    /**
     * Key 為紅外線設備名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態
     */
    private final Map<String, InfraredDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得指定紅外線設備的最新狀態
     */
    public InfraredDeviceStatus getLatest(String infraredName) {
        return statusMap.get(infraredName);
    }

    /**
     * 更新指定紅外線設備的最新狀態
     */
    public void put(String infraredName, InfraredDeviceStatus status) {
        statusMap.put(infraredName, status);
    }

    /**
     * 檢查是否已有快取資料
     */
    public boolean contains(String infraredName) {
        return statusMap.containsKey(infraredName);
    }

    /**
     * 清空所有快取資料（通常用於測試或重新啟動）
     */
    public void clear() {
        statusMap.clear();
    }
}
