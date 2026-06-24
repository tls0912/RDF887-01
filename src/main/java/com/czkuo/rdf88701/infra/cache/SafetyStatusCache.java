package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.safety.SafetyDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SafetyStatusCache
 * - 快取各安全設備（Bank）的最新狀態（來自 PLC Polling）
 * - 供監控器、API 查詢、UI 推播等模組取用
 *
 * Key：安全設備名稱（建議使用 plc-safety.yml 的 device.name，例如 "Safety-Sensor-Bank"）
 * Value：SafetyDeviceStatus（包含 snapshotTime 與 addr->state 對映）
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class SafetyStatusCache {

    private final Map<String, SafetyDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /** 取得最新狀態 */
    public SafetyDeviceStatus getLatest(String deviceName) {
        return statusMap.get(deviceName);
    }

    /** 更新對應設備的最新狀態 */
    public void put(String deviceName, SafetyDeviceStatus status) {
        statusMap.put(deviceName, status);
    }

    /** 是否已存在快取資料 */
    public boolean contains(String deviceName) {
        return statusMap.containsKey(deviceName);
    }

    /** 清除所有快取（單元測試或重啟時使用） */
    public void clear() {
        statusMap.clear();
    }
}
