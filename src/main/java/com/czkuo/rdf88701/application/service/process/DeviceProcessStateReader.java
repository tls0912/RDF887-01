package com.czkuo.rdf88701.application.service.process;

import com.czkuo.rdf88701.common.dto.DeviceProcessState;
import com.czkuo.rdf88701.common.enums.ProcessStatus;
import com.czkuo.rdf88701.infra.cache.DeviceProcessStateCache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeviceProcessStateReader {

    private final DeviceProcessStateCache cache;

    @Value("${monitor.process.ttl-ms:10000}")
    private long ttlMs;

    public Optional<DeviceProcessState> getFresh(String deviceName) {
        return cache.getFresh(deviceName, ttlMs);
    }

    public DeviceProcessState getBestEffort(String deviceName) {
        return cache.get(deviceName)
                .orElse(new DeviceProcessState(deviceName, ProcessStatus.STOP, "NoData"));
    }
}
