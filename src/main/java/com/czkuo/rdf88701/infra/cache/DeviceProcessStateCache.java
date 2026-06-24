package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.common.dto.DeviceProcessState;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe 的 in-memory 快取（不再抽介面）。
 * - update(): 寫入狀態（錯誤連續次數自動統計）
 * - get(): 取最後一次狀態（不檢查新鮮度）
 * - getFresh(): 依 TTL 檢查新鮮度
 * - snapshotAll(): 觀察用
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Component
public class DeviceProcessStateCache {

    public record CacheEntry(DeviceProcessState state, Instant updatedAt, int consecutiveErrors) {}

    private final ConcurrentHashMap<String, CacheEntry> map = new ConcurrentHashMap<>();

    public void update(DeviceProcessState state) {
        map.compute(state.getDeviceName(), (k, v) -> {
            int nextErr = state.getStatus() == ProcessStatus.ERROR
                    ? ((v == null) ? 1 : v.consecutiveErrors() + 1)
                    : 0;
            return new CacheEntry(state, Instant.now(), nextErr);
        });
    }

    public Optional<DeviceProcessState> get(String deviceName) {
        var entry = map.get(deviceName);
        return (entry == null) ? Optional.empty() : Optional.of(entry.state());
    }

    public Optional<DeviceProcessState> getFresh(String deviceName, long maxAgeMillis) {
        var entry = map.get(deviceName);
        if (entry == null) return Optional.empty();
        long age = Instant.now().toEpochMilli() - entry.updatedAt().toEpochMilli();
        return (age <= maxAgeMillis) ? Optional.of(entry.state()) : Optional.empty();
    }

    public Map<String, CacheEntry> snapshotAll() {
        return Map.copyOf(map);
    }

    /** 可選：清空（測試/維運用） */
    public void clear() {
        map.clear();
    }
}
