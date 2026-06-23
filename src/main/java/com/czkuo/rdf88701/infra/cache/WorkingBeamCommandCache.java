package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorkingBeamCommandCache
 * - 快取每台 Working Beam 的 PLC 指令狀態（Read區）與最後一次寫入指令（Write區）
 * - 完全使用 workingBeamId 作為 Map key，避免命名混淆
 */
@Component
public class WorkingBeamCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態
    private final Map<Integer, WorkingBeamCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態
    private final Map<Integer, WorkingBeamCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 合併後的狀態資料（Polling + LastWrite 補充）
    private final Map<Integer, WorkingBeamCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（從 PLC Polling 解碼而來）
     */
    public WorkingBeamCommandStatus getReadStatus(int beamId) {
        return latestReadStatusMap.get(beamId);
    }

    /**
     * 儲存最新讀取狀態（Polling 時呼叫）
     */
    public void updateReadStatus(int beamId, WorkingBeamCommandStatus status) {
        latestReadStatusMap.put(beamId, status);
    }

    /**
     * 取得最後一次寫入的指令內容（Word + Bit）
     */
    public WorkingBeamCommandStatus getLastWriteCommand(int beamId) {
        return lastWriteCommandMap.get(beamId);
    }

    /**
     * 更新最新寫入的指令（系統下發指令時呼叫）
     */
    public void updateLastWriteCommand(int beamId, WorkingBeamCommandStatus status) {
        lastWriteCommandMap.put(beamId, status);
    }

    /**
     * 快取中是否存在指定 Working Beam 的讀取狀態
     */
    public boolean containsReadStatus(int beamId) {
        return latestReadStatusMap.containsKey(beamId);
    }

    /**
     * 快取中是否存在指定 Working Beam 的寫入狀態
     */
    public boolean containsLastWrite(int beamId) {
        return lastWriteCommandMap.containsKey(beamId);
    }

    /**
     * 將讀取與寫入資料合併為一份（含補充歷史寫入指令）
     * - 注意：此方法會 clone 出新的對象，避免修改原始快取物件
     */
    public WorkingBeamCommandStatus getCombined(int beamId) {
        WorkingBeamCommandStatus read = getReadStatus(beamId);
        WorkingBeamCommandStatus write = getLastWriteCommand(beamId);

        if (read == null) return null;

        WorkingBeamCommandStatus combined = new WorkingBeamCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得目前已快取的合併版本（由 polling 解碼後放入）
     */
    public WorkingBeamCommandStatus getLatest(int beamId) {
        return combinedStatusMap.get(beamId);
    }

    /**
     * 寫入合併結果（通常於 polling 解碼完成後）
     */
    public void put(int beamId, WorkingBeamCommandStatus status) {
        combinedStatusMap.put(beamId, status);
    }

    /**
     * 移除已快取的合併版本（不影響 read/write 區）
     */
    public void remove(int beamId) {
        combinedStatusMap.remove(beamId);
    }

    /**
     * 清除指定 Working Beam 的所有快取（讀取 + 寫入 + 合併）
     */
    public void clear(int beamId) {
        latestReadStatusMap.remove(beamId);
        lastWriteCommandMap.remove(beamId);
        combinedStatusMap.remove(beamId);
    }
}
