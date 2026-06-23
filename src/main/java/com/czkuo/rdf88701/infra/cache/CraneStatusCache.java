package com.czkuo.rdf88701.infra.cache;

import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
