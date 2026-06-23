package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TransferCommandCache
 * - 快取每台 Transfer 裝置的 PLC 指令狀態（Read區）與最後一次寫入指令（Write區）
 * - 完全使用 transferId 作為 Map key，避免命名混淆
 */
@Component
public class TransferCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態
    private final Map<Integer, TransferCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態
    private final Map<Integer, TransferCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, TransferCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（從 PLC Polling 解碼而來）
     */
    public TransferCommandStatus getReadStatus(int transferId) {
        return latestReadStatusMap.get(transferId);
    }

    /**
     * 儲存最新讀取狀態（Polling 時呼叫）
     */
    public void updateReadStatus(int transferId, TransferCommandStatus status) {
        latestReadStatusMap.put(transferId, status);
    }

    /**
     * 取得最後一次寫入的指令內容（Word + Bit）
     */
    public TransferCommandStatus getLastWriteCommand(int transferId) {
        return lastWriteCommandMap.get(transferId);
    }

    /**
     * 更新最新寫入的指令（系統下發指令時呼叫）
     */
    public void updateLastWriteCommand(int transferId, TransferCommandStatus status) {
        lastWriteCommandMap.put(transferId, status);
    }

    /**
     * 快取中是否存在指定 Transfer 的讀取狀態
     */
    public boolean containsReadStatus(int transferId) {
        return latestReadStatusMap.containsKey(transferId);
    }

    /**
     * 快取中是否存在指定 Transfer 的寫入狀態
     */
    public boolean containsLastWrite(int transferId) {
        return lastWriteCommandMap.containsKey(transferId);
    }

    /**
     * 將讀取與寫入資料合併為一份（含補充歷史寫入指令）
     * - 注意：此方法會 clone 出新的對象，避免修改原始快取物件
     */
    public TransferCommandStatus getCombined(int transferId) {
        TransferCommandStatus read = getReadStatus(transferId);
        TransferCommandStatus write = getLastWriteCommand(transferId);

        if (read == null) return null;

        TransferCommandStatus combined = new TransferCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前已快取的合併版本（由 polling 解碼後放入）
     */
    public TransferCommandStatus getLatest(int transferId) {
        return combinedStatusMap.get(transferId);
    }

    /**
     * 寫入合併結果（通常於 polling 解碼完成後）
     */
    public void put(int transferId, TransferCommandStatus status) {
        combinedStatusMap.put(transferId, status);
    }

    /**
     * 移除已快取的合併版本（不影響 read/write 區）
     */
    public void remove(int transferId) {
        combinedStatusMap.remove(transferId);
    }

    /**
     * 清除指定 Transfer 的所有快取（讀取 + 寫入 + 合併）
     */
    public void clear(int transferId) {
        latestReadStatusMap.remove(transferId);
        lastWriteCommandMap.remove(transferId);
        combinedStatusMap.remove(transferId);
    }
}
