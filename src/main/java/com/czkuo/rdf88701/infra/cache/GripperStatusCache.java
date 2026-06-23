package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GripperStatusCache
 * - 快取各 Gripper 裝置的最新狀態（來自 PLC Polling）
 * - 可由狀態機、監控器、UI 推播等模組查詢
 */
@Component
public class GripperStatusCache {

    /**
     * Key 為 Gripper 名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態
     */
    private final Map<String, GripperDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新狀態
     */
    public GripperDeviceStatus getLatest(String gripperName) {
        return statusMap.get(gripperName);
    }

    /**
     * 更新對應 Gripper 的最新狀態
     */
    public void put(String gripperName, GripperDeviceStatus status) {
        statusMap.put(gripperName, status);
    }

    /**
     * 判斷是否已存在快取資料
     */
    public boolean contains(String gripperName) {
        return statusMap.containsKey(gripperName);
    }

    /**
     * 清除所有快取（單元測試或重啟時使用）
     */
    public void clear() {
        statusMap.clear();
    }
}
