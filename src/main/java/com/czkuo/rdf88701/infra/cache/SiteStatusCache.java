package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SiteStatusCache
 * 快取各 Site 裝置的最新狀態（來自 PLC Polling）
 * 提供統一入口供狀態機、監控器、UI 推播模組查詢
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class SiteStatusCache {

    /**
     * Key 為 Site 名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態快照
     */
    private final Map<String, SiteDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得指定 Site 的最新狀態
     *
     * @param siteName Site 裝置名稱
     * @return 最新 Site 狀態，若無則為 null
     */
    public SiteDeviceStatus getLatest(String siteName) {
        return statusMap.get(siteName);
    }

    /**
     * 更新指定 Site 的狀態快取
     *
     * @param siteName Site 裝置名稱
     * @param status   最新狀態
     */
    public void put(String siteName, SiteDeviceStatus status) {
        statusMap.put(siteName, status);
    }

    /**
     * 檢查指定 Site 是否已存在狀態快取
     *
     * @param siteName Site 裝置名稱
     * @return true 表示有快取
     */
    public boolean contains(String siteName) {
        return statusMap.containsKey(siteName);
    }

    /**
     * 清除所有 Site 快取（通常用於系統重啟或測試）
     */
    public void clear() {
        statusMap.clear();
    }
}
