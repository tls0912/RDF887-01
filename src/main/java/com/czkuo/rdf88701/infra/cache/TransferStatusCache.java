package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransferStatusCache
 * - 快取各 Transfer 裝置的最新狀態（來自 PLC Polling）
 * - 可由狀態機、監控器、UI 推播等模組查詢
 */
@Component
public class TransferStatusCache {

    /**
     * Key 為 Transfer 名稱（建議使用配置檔中的名稱），Value 為最新裝置狀態
     */
    private final Map<String, TransferDeviceStatus> statusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新狀態
     */
    public TransferDeviceStatus getLatest(String transferName) {
        return statusMap.get(transferName);
    }

    /**
     * 更新對應 Transfer 的最新狀態
     */
    public void put(String transferName, TransferDeviceStatus status) {
        statusMap.put(transferName, status);
    }

    /**
     * 判斷是否已存在快取資料
     */
    public boolean contains(String transferName) {
        return statusMap.containsKey(transferName);
    }

    /**
     * 清除所有快取（單元測試或重啟時使用）
     */
    public void clear() {
        statusMap.clear();
    }
}
