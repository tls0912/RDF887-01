package com.czkuo.rdf88701.common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 錯誤抑制工具（Error Suppression Utility）
 * - 用於防止同一事件在短時間內重複刷大量 log。
 * - 每個 key（如設備編號）獨立控管抑制時間。
 */
public class ErrorSuppressor {

    /** 各 Key 最近一次觸發錯誤的時間 */
    private final Map<Object, Long> lastErrorTimeMap = new ConcurrentHashMap<>();

    /** 抑制時間（毫秒），例如 60 秒 = 60000ms */
    private final long suppressionIntervalMs;

    /**
     * 建立錯誤抑制器
     * @param suppressionIntervalMs 抑制時間，單位毫秒
     */
    public ErrorSuppressor(long suppressionIntervalMs) {
        this.suppressionIntervalMs = suppressionIntervalMs;
    }

    /**
     * 判斷該 key 是否可以觸發錯誤訊息（超過抑制時間）
     * @param key 任意可以辨識錯誤來源的 key（如 gripperId、deviceName）
     * @return true = 可以 log；false = 還在抑制期間
     */
    public boolean shouldLog(Object key) {
        long now = System.currentTimeMillis();
        long lastTime = lastErrorTimeMap.getOrDefault(key, 0L);
        if (now - lastTime > suppressionIntervalMs) {
            lastErrorTimeMap.put(key, now);
            return true;
        } else {
            // 更新最後一次錯誤時間，避免一直很舊
            lastErrorTimeMap.put(key, now);
            return false;
        }
    }
}
