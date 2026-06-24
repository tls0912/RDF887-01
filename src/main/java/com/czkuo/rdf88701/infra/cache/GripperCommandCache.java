package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GripperCommandCache
 * - 快取每台 Gripper 裝置的 PLC 指令狀態（Write區資料）
 * - 包含 Polling 解碼的 Bit + Word 指令，以及 PC 寫入的歷史指令內容
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class GripperCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態
    private final Map<Integer, GripperCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態
    private final Map<Integer, GripperCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, GripperCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（由 PLC Polling 解碼）
     */
    public GripperCommandStatus getReadStatus(int gripperId) {
        return latestReadStatusMap.get(gripperId);
    }

    /**
     * 更新最新讀取狀態（Polling 呼叫）
     */
    public void updateReadStatus(int gripperId, GripperCommandStatus status) {
        latestReadStatusMap.put(gripperId, status);
    }

    /**
     * 取得最後一次寫入的指令內容（Bit + Word）
     */
    public GripperCommandStatus getLastWriteCommand(int gripperId) {
        return lastWriteCommandMap.get(gripperId);
    }

    /**
     * 更新最新寫入的指令（系統下發時呼叫）
     */
    public void updateLastWriteCommand(int gripperId, GripperCommandStatus status) {
        lastWriteCommandMap.put(gripperId, status);
    }

    /**
     * 是否已存在指定 Gripper 的讀取狀態
     */
    public boolean containsReadStatus(int gripperId) {
        return latestReadStatusMap.containsKey(gripperId);
    }

    /**
     * 是否已存在指定 Gripper 的寫入狀態
     */
    public boolean containsLastWrite(int gripperId) {
        return lastWriteCommandMap.containsKey(gripperId);
    }

    /**
     * 將讀取與寫入資料合併為一份（不會影響原始快取內容）
     */
    public GripperCommandStatus getCombined(int gripperId) {
        GripperCommandStatus read = getReadStatus(gripperId);
        GripperCommandStatus write = getLastWriteCommand(gripperId);

        if (read == null) return null;

        GripperCommandStatus combined = new GripperCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前最新合併版本（由 polling 解碼後放入）
     */
    public GripperCommandStatus getLatest(int gripperId) {
        return combinedStatusMap.get(gripperId);
    }

    /**
     * 寫入合併後的狀態（通常於 polling 組合完成後）
     */
    public void put(int gripperId, GripperCommandStatus status) {
        combinedStatusMap.put(gripperId, status);
    }

    /**
     * 移除已快取的合併版本（不影響 read/write）
     */
    public void remove(int gripperId) {
        combinedStatusMap.remove(gripperId);
    }

    /**
     * 清除指定 Gripper 的所有快取（read + write + combined）
     */
    public void clear(int gripperId) {
        latestReadStatusMap.remove(gripperId);
        lastWriteCommandMap.remove(gripperId);
        combinedStatusMap.remove(gripperId);
    }
}
