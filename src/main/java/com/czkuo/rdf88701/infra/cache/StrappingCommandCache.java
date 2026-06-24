package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StrappingCommandCache
 * - 快取每台 Strapping 裝置的 PLC 指令狀態（Read區）與最後一次寫入指令（Write區）
 * - 完全使用 strappingId 作為 Map key，避免命名混淆
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class StrappingCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態
    private final Map<Integer, StrappingCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態
    private final Map<Integer, StrappingCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, StrappingCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（從 PLC Polling 解碼而來）
     */
    public StrappingCommandStatus getReadStatus(int strappingId) {
        return latestReadStatusMap.get(strappingId);
    }

    /**
     * 儲存最新讀取狀態（Polling 時呼叫）
     */
    public void updateReadStatus(int strappingId, StrappingCommandStatus status) {
        latestReadStatusMap.put(strappingId, status);
    }

    /**
     * 取得最後一次寫入的指令內容（Word + Bit）
     */
    public StrappingCommandStatus getLastWriteCommand(int strappingId) {
        return lastWriteCommandMap.get(strappingId);
    }

    /**
     * 更新最新寫入的指令（系統下發指令時呼叫）
     */
    public void updateLastWriteCommand(int strappingId, StrappingCommandStatus status) {
        lastWriteCommandMap.put(strappingId, status);
    }

    /**
     * 快取中是否存在指定 Strapping 的讀取狀態
     */
    public boolean containsReadStatus(int strappingId) {
        return latestReadStatusMap.containsKey(strappingId);
    }

    /**
     * 快取中是否存在指定 Strapping 的寫入狀態
     */
    public boolean containsLastWrite(Long strappingId) {
        return lastWriteCommandMap.containsKey(strappingId);
    }

    /**
     * 將讀取與寫入資料合併為一份（含補充歷史寫入指令）
     * - 注意：此方法會 clone 出新的對象，避免修改原始快取物件
     */
    public StrappingCommandStatus getCombined(int strappingId) {
        StrappingCommandStatus read = getReadStatus(strappingId);
        StrappingCommandStatus write = getLastWriteCommand(strappingId);

        if (read == null) return null;

        StrappingCommandStatus combined = new StrappingCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前已快取的合併版本（由 polling 解碼後放入）
     */
    public StrappingCommandStatus getLatest(int strappingId) {
        return combinedStatusMap.get(strappingId);
    }

    /**
     * 寫入合併結果（通常於 polling 解碼完成後）
     */
    public void put(int strappingId, StrappingCommandStatus status) {
        combinedStatusMap.put(strappingId, status);
    }

    /**
     * 移除已快取的合併版本（不影響 read/write 區）
     */
    public void remove(int strappingId) {
        combinedStatusMap.remove(strappingId);
    }

    /**
     * 清除指定 Strapping 的所有快取（讀取 + 寫入 + 合併）
     */
    public void clear(int strappingId) {
        latestReadStatusMap.remove(strappingId);
        lastWriteCommandMap.remove(strappingId);
        combinedStatusMap.remove(strappingId);
    }
}
