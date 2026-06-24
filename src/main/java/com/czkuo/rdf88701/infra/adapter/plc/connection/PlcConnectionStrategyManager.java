package com.czkuo.rdf88701.infra.adapter.plc.connection;

import com.czkuo.rdf88701.config.plc.PlcDeviceRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PLC 連線策略管理器。
 *
 * <p>以 deviceName + port 為單位記錄連線失敗次數與最後失敗時間，提供暫時熔斷
 * 與可用 port 篩選，讓 PlcClientManager 在多 port 裝置上執行 failover。</p>
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlcConnectionStrategyManager {

    private final PlcDeviceRegistry deviceRegistry;

    /** 預設最大允許重試次數（若裝置未指定 max-retry-per-port） */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /** 預設熔斷時間（毫秒，若裝置未指定 circuit-break-ms） */
    private static final long DEFAULT_CIRCUIT_BREAK_MS = 30_000L;

    /** 每個 deviceName:port 的錯誤紀錄（包含失敗次數與最後失敗時間） */
    private final Map<String, PortFailureRecord> failureMap = new ConcurrentHashMap<>();

    /**
     * 檢查指定 port 是否處於暫時封鎖（熔斷）狀態
     * 若該 port 的錯誤次數超過上限，且尚在熔斷冷卻期間，則視為封鎖中
     */
    public boolean isPortTemporarilyBlocked(String deviceName, int port) {
        String key = devicePortKey(deviceName, port);
        PortFailureRecord record = failureMap.get(key);
        if (record == null) return false;

        int maxRetries = deviceRegistry.getOptionInt(deviceName, "max-retry-per-port", DEFAULT_MAX_RETRIES);
        long circuitBreakMs = deviceRegistry.getOptionLong(deviceName, "circuit-break-ms", DEFAULT_CIRCUIT_BREAK_MS);

        if (record.getFailureCount() >= maxRetries &&
                record.getLastFailedTime().plusMillis(circuitBreakMs).isAfter(Instant.now())) {
            log.warn("[PLC] 熔斷：裝置 '{}' 的 port {} 暫時封鎖中", deviceName, port);
            return true;
        }
        return false;
    }

    /**
     * 標記指定 port 的連線失敗紀錄
     * 會累加失敗次數，並更新最後失敗時間
     */
    public void markPortFailure(String deviceName, int port) {
        String key = devicePortKey(deviceName, port);
        failureMap.compute(key, (k, v) -> {
            if (v == null) v = new PortFailureRecord();
            v.failureCount++;
            v.lastFailedTime = Instant.now();
            return v;
        });
    }

    /**
     * 標記 port 成功連線，會移除原有的失敗紀錄（解除熔斷）
     */
    public void markPortSuccess(String deviceName, int port) {
        String key = devicePortKey(deviceName, port);
        failureMap.remove(key);
    }

    /**
     * 根據目前熔斷狀態，篩選出仍可用的 port 清單
     */
    public List<Integer> getAvailablePorts(String deviceName, List<Integer> ports) {
        List<Integer> result = new ArrayList<>();
        for (Integer port : ports) {
            if (!isPortTemporarilyBlocked(deviceName, port)) {
                result.add(port);
            }
        }
        return result;
    }

    /**
     * 建立 deviceName:port 組合 key
     */
    private String devicePortKey(String deviceName, int port) {
        return deviceName + ":" + port;
    }

    /**
     * 每個 port 的失敗紀錄
     * 包含：失敗次數與最後一次失敗時間
     */
    @Getter
    private static class PortFailureRecord {
        private int failureCount = 0;
        private Instant lastFailedTime = Instant.EPOCH;

    }
}
