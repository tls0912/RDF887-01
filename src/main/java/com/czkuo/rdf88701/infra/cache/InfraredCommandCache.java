package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InfraredCommandCache
 * - 快取每台紅外線設備的指令狀態（PLC Polling 解碼 + 最後一次寫入）
 * - 完全使用 infraredId 作為 Map key，避免名稱混淆
 */
@Component
public class InfraredCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態（Read區）
    private final Map<Integer, InfraredCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態（Write區）
    private final Map<Integer, InfraredCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, InfraredCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（PLC polling 解碼結果）
     */
    public InfraredCommandStatus getReadStatus(int infraredId) {
        return latestReadStatusMap.get(infraredId);
    }

    /**
     * 更新最新讀取狀態
     */
    public void updateReadStatus(int infraredId, InfraredCommandStatus status) {
        latestReadStatusMap.put(infraredId, status);
    }

    /**
     * 取得最後一次寫入的命令（由 PC 發出）
     */
    public InfraredCommandStatus getLastWriteCommand(int infraredId) {
        return lastWriteCommandMap.get(infraredId);
    }

    /**
     * 更新最後一次寫入的命令
     */
    public void updateLastWriteCommand(int infraredId, InfraredCommandStatus status) {
        lastWriteCommandMap.put(infraredId, status);
    }

    /**
     * 檢查是否有 polling 快取
     */
    public boolean containsReadStatus(int infraredId) {
        return latestReadStatusMap.containsKey(infraredId);
    }

    /**
     * 檢查是否有寫入命令快取
     */
    public boolean containsLastWrite(int infraredId) {
        return lastWriteCommandMap.containsKey(infraredId);
    }

    /**
     * 建立 polling + write 合併版本（clone 一份）
     */
    public InfraredCommandStatus getCombined(int infraredId) {
        InfraredCommandStatus read = getReadStatus(infraredId);
        InfraredCommandStatus write = getLastWriteCommand(infraredId);

        if (read == null) return null;

        InfraredCommandStatus combined = new InfraredCommandStatus();
        combined.cloneContentFrom(read);

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前最新的合併快取（由 polling 完成後 put）
     */
    public InfraredCommandStatus getLatest(int infraredId) {
        return combinedStatusMap.get(infraredId);
    }

    /**
     * 設定合併快取
     */
    public void put(int infraredId, InfraredCommandStatus status) {
        combinedStatusMap.put(infraredId, status);
    }

    /**
     * 移除合併快取（不影響 read/write）
     */
    public void remove(int infraredId) {
        combinedStatusMap.remove(infraredId);
    }

    /**
     * 清除指定紅外線設備的所有快取（read/write/combined）
     */
    public void clear(int infraredId) {
        latestReadStatusMap.remove(infraredId);
        lastWriteCommandMap.remove(infraredId);
        combinedStatusMap.remove(infraredId);
    }
}
