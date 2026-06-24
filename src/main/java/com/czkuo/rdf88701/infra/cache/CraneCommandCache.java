package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CraneCommandCache
 * - 快取每台 Crane 的最新 PLC 指令狀態（Read 區）與最後一次寫入指令（Write 區）
 * - 完全使用 craneId 作為 Map key，避免 craneName 混淆
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class CraneCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態
    private final Map<Integer, CraneCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態
    private final Map<Integer, CraneCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, CraneCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（從 PLC Polling 解碼而來）
     */
    public CraneCommandStatus getReadStatus(int craneId) {
        return latestReadStatusMap.get(craneId);
    }

    /**
     * 儲存最新讀取狀態（Polling 時呼叫）
     */
    public void updateReadStatus(int craneId, CraneCommandStatus status) {
        latestReadStatusMap.put(craneId, status);
    }

    /**
     * 取得最後一次寫入的指令內容（Word + Bit）
     */
    public CraneCommandStatus getLastWriteCommand(int craneId) {
        return lastWriteCommandMap.get(craneId);
    }

    /**
     * 更新最新寫入的指令（系統下發指令時呼叫）
     */
    public void updateLastWriteCommand(int craneId, CraneCommandStatus status) {
        lastWriteCommandMap.put(craneId, status);
    }

    /**
     * 快取中是否存在指定天車的讀取狀態
     */
    public boolean containsReadStatus(int craneId) {
        return latestReadStatusMap.containsKey(craneId);
    }

    /**
     * 快取中是否存在指定天車的寫入狀態
     */
    public boolean containsLastWrite(int craneId) {
        return lastWriteCommandMap.containsKey(craneId);
    }

    /**
     * 將讀取與寫入資料合併為一份（含補充歷史寫入指令）
     * - 注意：此方法會 clone 出新的對象，避免修改原始快取物件
     */
    public CraneCommandStatus getCombined(int craneId) {
        CraneCommandStatus read = getReadStatus(craneId);
        CraneCommandStatus write = getLastWriteCommand(craneId);

        if (read == null) return null;

        CraneCommandStatus combined = new CraneCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前已快取的合併版本（由 polling 解碼後放入）
     */
    public CraneCommandStatus getLatest(int craneId) {
        return combinedStatusMap.get(craneId);
    }

    /**
     * 寫入合併結果（通常於 polling 解碼完成後）
     */
    public void put(int craneId, CraneCommandStatus status) {
        combinedStatusMap.put(craneId, status);
    }

    /**
     * 移除已快取的合併版本（不影響 read/write 區）
     */
    public void remove(int craneId) {
        combinedStatusMap.remove(craneId);
    }

    /**
     * 清除指定天車的所有快取（讀取 + 寫入 + 合併）
     */
    public void clear(int craneId) {
        latestReadStatusMap.remove(craneId);
        lastWriteCommandMap.remove(craneId);
        combinedStatusMap.remove(craneId);
    }
}
