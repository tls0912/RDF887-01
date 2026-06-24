package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SiteCommandCache
 * 快取每台 Site 裝置的 PLC 指令狀態（Read區）與最後一次寫入指令（Write區）
 * 完全使用 siteId 作為 Map key，避免命名混淆與重複
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class SiteCommandCache {

    // 最新從 PLC Polling 解碼的回應狀態（Read區）
    private final Map<Integer, SiteCommandStatus> latestReadStatusMap = new ConcurrentHashMap<>();

    // 最後一次由 PC 寫入的指令狀態（Write區）
    private final Map<Integer, SiteCommandStatus> lastWriteCommandMap = new ConcurrentHashMap<>();

    // 已合併的最新狀態（Read + Write補充）
    private final Map<Integer, SiteCommandStatus> combinedStatusMap = new ConcurrentHashMap<>();

    /**
     * 取得最新讀取狀態（Read區資料）
     */
    public SiteCommandStatus getReadStatus(int siteId) {
        return latestReadStatusMap.get(siteId);
    }

    /**
     * 儲存最新讀取狀態（PLC 輪詢時呼叫）
     */
    public void updateReadStatus(int siteId, SiteCommandStatus status) {
        latestReadStatusMap.put(siteId, status);
    }

    /**
     * 取得最後一次寫入的指令狀態（Write區資料）
     */
    public SiteCommandStatus getLastWriteCommand(int siteId) {
        return lastWriteCommandMap.get(siteId);
    }

    /**
     * 更新寫入指令快取（指令發送時呼叫）
     */
    public void updateLastWriteCommand(int siteId, SiteCommandStatus status) {
        lastWriteCommandMap.put(siteId, status);
    }

    /**
     * 是否存在快取的 Read 區狀態
     */
    public boolean containsReadStatus(int siteId) {
        return latestReadStatusMap.containsKey(siteId);
    }

    /**
     * 是否存在快取的最後寫入指令
     */
    public boolean containsLastWrite(int siteId) {
        return lastWriteCommandMap.containsKey(siteId);
    }

    /**
     * 建立合併狀態資料（Read 區 + Write 補充）
     * - clone 原始資料以避免外部修改快取內容
     */
    public SiteCommandStatus getCombined(int siteId) {
        SiteCommandStatus read = getReadStatus(siteId);
        SiteCommandStatus write = getLastWriteCommand(siteId);

        if (read == null) return null;

        SiteCommandStatus combined = new SiteCommandStatus();
        combined.cloneContentFrom(read); // 建立複本

        if (write != null) {
            combined.setLastWriteCommand(write);
        }

        return combined;
    }

    /**
     * 取得快取中最新合併狀態
     */
    public SiteCommandStatus getLatest(int siteId) {
        return combinedStatusMap.get(siteId);
    }

    /**
     * 快取合併結果（通常於 polling 結束時）
     */
    public void put(int siteId, SiteCommandStatus status) {
        combinedStatusMap.put(siteId, status);
    }

    /**
     * 移除指定 Site 的合併狀態（不影響 read/write）
     */
    public void remove(int siteId) {
        combinedStatusMap.remove(siteId);
    }

    /**
     * 清除指定 Site 的所有狀態快取
     */
    public void clear(int siteId) {
        latestReadStatusMap.remove(siteId);
        lastWriteCommandMap.remove(siteId);
        combinedStatusMap.remove(siteId);
    }
}
