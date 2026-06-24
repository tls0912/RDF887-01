package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */

@Component
public class CraneStatusCache {

    private final Map<String, CraneDeviceStatus> statusMap = new ConcurrentHashMap<>();

    public CraneDeviceStatus getLatest(String craneName) {
        return statusMap.get(craneName);
    }

    public void put(String craneName, CraneDeviceStatus status) {
        statusMap.put(craneName, status);
    }

    public boolean contains(String craneName) {
        return statusMap.containsKey(craneName);
    }
}
