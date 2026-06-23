package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcCraneProperties;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.crane.CraneStateMachine;
import com.czkuo.rdf88701.infra.cache.CraneCommandCache;
import com.czkuo.rdf88701.infra.cache.CraneStatusCache;
import com.czkuo.rdf88701.infra.decoder.CraneCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.CraneDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.crane.CraneStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.CraneMemoryLayoutService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * CranePollingHandler
 * - 負責處理所有 PLC 資料解析、狀態合併與推播。
 * - 支援 Crane 回應資料與 PLC 寫入控制指令（PlcCommandStatus）的解析與監控。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CranePollingHandler implements PollingHandler {

    private final PlcCraneProperties properties;
    private final CraneDataDecoder dataDecoder;
    private final CraneCommandDecoder commandDecoder;
    private final CraneMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;
    private final CraneStatusCache statusCache;
    private final CraneCommandCache commandStatusCache;

    private final Map<Integer, CraneDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, CraneDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, CraneCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, CraneCommandStatus> cmdWordCache = new ConcurrentHashMap<>();
    private final Map<Integer, CraneStateMachine> stateMachines = new ConcurrentHashMap<>();

    private final List<CraneStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<CraneCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;

    public void initCraneStateMachines() {
        for (PlcCraneProperties.Crane crane : properties.getCranes()) {
            stateMachines.put(crane.getId(), new CraneStateMachine(crane.getId()));
        }
        log.info("[INIT] Crane 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    public void handleBitData(int craneId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcCraneProperties.Crane crane = findCraneById(craneId);
        if (crane == null) return;

        int bitPollEnd = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;
        if (isWriteBitArea(crane, fullStartAddress, bitPollEnd)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(craneId, "write", "B", fullData, fullStartAddress);
            CraneCommandStatus status = new CraneCommandStatus();
            commandDecoder.decodeBits(actual, status);
            status.setSnapshotTime(snapshotTime); // 使用傳入的時間
            cmdBitCache.put(craneId, status);
            tryCombineCommand(craneId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(craneId, "read", "B", fullData, fullStartAddress);
            CraneDeviceStatus status = new CraneDeviceStatus();
            dataDecoder.decodeBits(actual, status);
            status.setAvailable(true);
            status.setSnapshotTime(snapshotTime); // 使用傳入的時間
            bitCache.put(craneId, status);
            tryCombine(craneId);
        }
    }

    public void handleWordData(int craneId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcCraneProperties.Crane crane = findCraneById(craneId);
        if (crane == null) return;

        int wordPollEnd = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;
        if (isWriteWordArea(crane, fullStartAddress, wordPollEnd)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(craneId, "write", "W", fullData, fullStartAddress);
            CraneCommandStatus status = new CraneCommandStatus();
            commandDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime); // 使用傳入的時間
            cmdWordCache.put(craneId, status);
            tryCombineCommand(craneId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(craneId, "read", "W", fullData, fullStartAddress);
            CraneDeviceStatus status = new CraneDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setAvailable(true);
            status.setSnapshotTime(snapshotTime); // 使用傳入的時間
            wordCache.put(craneId, status);
            tryCombine(craneId);
        }
    }

    private void tryCombine(int craneId) {
        CraneDeviceStatus bits = bitCache.get(craneId);
        CraneDeviceStatus words = wordCache.get(craneId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Crane#{} Snapshot 差距過大：{}ms", craneId, delta);
            return;
        }

        CraneDeviceStatus combined = new CraneDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setCraneId(craneId);

        CraneStateMachine machine = stateMachines.get(craneId);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            CraneStatusUpdatedEvent event = new CraneStatusUpdatedEvent(craneId, combined, machine.getCurrentState());
            pendingEvents.add(event);
        }
        statusCache.put("Crane#" + craneId, combined);

        bitCache.remove(craneId);
        wordCache.remove(craneId);
    }

    private void tryCombineCommand(int craneId) {
        CraneCommandStatus bits = cmdBitCache.get(craneId);
        CraneCommandStatus words = cmdWordCache.get(craneId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] CraneCommand#{} Snapshot 差距過大：{}ms", craneId, delta);
            return;
        }

        CraneCommandStatus combined = new CraneCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setCraneId(craneId);

        CraneCommandStatus previous = commandStatusCache.getLatest(craneId);
        if (combined.hasMeaningfulChange(previous)) {
            CraneCommandUpdatedEvent event = new CraneCommandUpdatedEvent(craneId, combined);
            pendingCommandEvents.add(event);
        }
        commandStatusCache.put(craneId, combined);

        cmdBitCache.remove(craneId);
        cmdWordCache.remove(craneId);
    }

    private boolean isWriteBitArea(PlcCraneProperties.Crane crane, int pollStart, int pollEnd) {
        return crane.getWriteAreas().stream().anyMatch(a -> a.getType().equalsIgnoreCase("B") && pollStart <= (a.getAddress() + a.getLength() - 1) && pollEnd >= a.getAddress());
    }

    private boolean isWriteWordArea(PlcCraneProperties.Crane crane, int pollStart, int pollEnd) {
        return crane.getWriteAreas().stream().anyMatch(a -> a.getType().equalsIgnoreCase("W") && pollStart <= (a.getAddress() + a.getLength() - 1) && pollEnd >= a.getAddress());
    }

    private PlcCraneProperties.Crane findCraneById(int id) {
        return properties.getCranes().stream().filter(c -> c.getId() == id).findFirst().orElse(null);
    }

    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<CraneStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            //eventPublisher.publishCraneStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Crane startDevicePushTask", () -> eventPublisher.publishCraneStatusUpdatedBatch(batch));
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<CraneCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            //eventPublisher.publishCraneCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Crane startCommandPushTask", () -> eventPublisher.publishCraneCommandUpdatedBatch(batch));
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    public void stopDevicePushTask() {
        if (devicePushTask != null) devicePushTask.cancel(true);
    }

    public void stopCommandPushTask() {
        if (commandPushTask != null) commandPushTask.cancel(true);
    }

    public void startDeviceMonitoring() {
        deviceMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Integer, CraneStateMachine> entry : stateMachines.entrySet()) {
                int craneId = entry.getKey();
                CraneDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    CraneStatusOverdueEvent event = new CraneStatusOverdueEvent(craneId, latest);
                    eventPublisher.publishCraneStatusOverdue(event);
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcCraneProperties.Crane crane : properties.getCranes()) {
                int craneId = crane.getId();
                CraneCommandStatus status = commandStatusCache.getLatest(craneId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    CraneCommandOverdueEvent event = new CraneCommandOverdueEvent(craneId, status);
                    eventPublisher.publishCraneCommandOverdue(event);
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void stopDeviceMonitoring() {
        if (deviceMonitorTask != null) deviceMonitorTask.cancel(true);
    }

    public void stopCommandMonitoring() {
        if (commandMonitorTask != null) commandMonitorTask.cancel(true);
    }

    public CraneCommandStatus getLatestCommandStatus(int craneId) {
        return commandStatusCache.getLatest(craneId);
    }
}
