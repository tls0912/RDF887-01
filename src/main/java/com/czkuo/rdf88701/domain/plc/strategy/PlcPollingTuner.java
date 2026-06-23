package com.czkuo.rdf88701.domain.plc.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLC 輪詢頻率調整器：
 * 根據成功/失敗次數動態調整每個裝置的輪詢頻率（採用滑動視窗統計）
 */
@Component
public class PlcPollingTuner {

    /** 滑動視窗最多保留最近 N 筆紀錄 */
    private static final int MAX_WINDOW_SIZE = 100;

    /** 可調整的最大/最小比例限制 */
    private static final double MAX_RATE_UP = 0.5;   // 最快間隔縮短一半
    private static final double MAX_RATE_DOWN = 10.0; // 最慢間隔拉長兩倍

    /** 每個裝置的統計資料 */
    private final Map<String, PollingStats> statsMap = new ConcurrentHashMap<>();

    /** 記錄一次成功通訊 */
    public void recordSuccess(String deviceName) {
        getStats(deviceName).record(true);
    }

    /** 記錄一次失敗通訊 */
    public void recordFailure(String deviceName) {
        getStats(deviceName).record(false);
    }

    /** 根據近一段時間的通訊狀況，回傳建議的輪詢間隔 */
    public long getAdjustedInterval(String deviceName, long defaultIntervalMs) {
        PollingStats stats = getStats(deviceName);

        int total = stats.window.size();
        if (total < 50) return defaultIntervalMs; // 資料過少，不調整

        long successCount = stats.window.stream().filter(b -> b).count();
        double successRate = successCount / (double) total;

        if (successRate > 0.8) {
            return Math.max((long) (defaultIntervalMs * MAX_RATE_UP), 10); // 至少 100ms
        } else if (successRate < 0.5) {
            return (long) (defaultIntervalMs * MAX_RATE_DOWN);
        } else {
            return defaultIntervalMs;
        }
    }

    /** 取得裝置對應的統計視窗 */
    private PollingStats getStats(String name) {
        return statsMap.computeIfAbsent(name, k -> new PollingStats());
    }

    /** 裝置通訊狀態滑動視窗統計 */
    private static class PollingStats {
        Deque<Boolean> window = new ArrayDeque<>();

        void record(boolean success) {
            if (window.size() >= MAX_WINDOW_SIZE) {
                window.pollFirst();
            }
            window.addLast(success);
        }
    }
}
