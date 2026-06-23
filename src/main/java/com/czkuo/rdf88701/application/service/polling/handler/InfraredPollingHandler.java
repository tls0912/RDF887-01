package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcInfraredProperties;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.infrared.InfraredStateMachine;
import com.czkuo.rdf88701.infra.cache.InfraredCommandCache;
import com.czkuo.rdf88701.infra.cache.InfraredStatusCache;
import com.czkuo.rdf88701.infra.decoder.InfraredCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.InfraredDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.infrared.InfraredStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.InfraredMemoryLayoutService;
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
 * InfraredPollingHandler
 * - 處理紅外線測距設備的輪詢資料
 * - 將 PLC Bit/Word 資料解碼為裝置狀態與控制指令
 * - 執行狀態合併、推播與過期檢查任務
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InfraredPollingHandler implements PollingHandler {

    private final PlcInfraredProperties properties;
    private final InfraredDataDecoder dataDecoder;
    private final InfraredCommandDecoder commandDecoder;
    private final InfraredMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;
    private final InfraredStatusCache statusCache;
    private final InfraredCommandCache commandStatusCache;

    private final Map<Integer, InfraredDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, InfraredDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, InfraredCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, InfraredCommandStatus> cmdWordCache = new ConcurrentHashMap<>();
    private final Map<Integer, InfraredStateMachine> stateMachines = new ConcurrentHashMap<>();

    private final List<InfraredStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<InfraredCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(6);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;
    /**
     * 初始化所有紅外線設備的狀態機
     */
    public void initInfraredStateMachines() {
        for (PlcInfraredProperties.Infrared device : properties.getInfrareds()) {
            stateMachines.put(device.getId(), new InfraredStateMachine(device.getId()));
        }
        log.info("[INIT] Infrared 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    @Override
    public void handleBitData(int infraredId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcInfraredProperties.Infrared device = findDeviceById(infraredId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;

        if (isWriteBitArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(infraredId, "write", "B", fullData, fullStartAddress);
            InfraredCommandStatus cmd = new InfraredCommandStatus();
            commandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(infraredId, cmd);
            tryCombineCommand(infraredId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(infraredId, "read", "B", fullData, fullStartAddress);
            InfraredDeviceStatus status = new InfraredDeviceStatus();
            dataDecoder.decodeBits(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            bitCache.put(infraredId, status);
            tryCombine(infraredId);
        }
    }

    @Override
    public void handleWordData(int infraredId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcInfraredProperties.Infrared device = findDeviceById(infraredId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;

        if (isWriteWordArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(infraredId, "write", "W", fullData, fullStartAddress);
            InfraredCommandStatus cmd = new InfraredCommandStatus();
            commandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(infraredId, cmd);
            tryCombineCommand(infraredId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(infraredId, "read", "W", fullData, fullStartAddress);
            InfraredDeviceStatus status = new InfraredDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            wordCache.put(infraredId, status);
            tryCombine(infraredId);
        }
    }

    private void tryCombine(int id) {
        InfraredDeviceStatus bits = bitCache.get(id);
        InfraredDeviceStatus words = wordCache.get(id);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Infrared#{} Snapshot 差距過大：{}ms", id, delta);
            return;
        }

        InfraredDeviceStatus combined = new InfraredDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setInfraredId(id);

        InfraredStateMachine machine = stateMachines.get(id);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            pendingEvents.add(new InfraredStatusUpdatedEvent(id, combined, machine.getCurrentState()));
        }
        statusCache.put("InfraredDistance#" + id, combined);
        bitCache.remove(id);
        wordCache.remove(id);
    }

    private void tryCombineCommand(int id) {
        InfraredCommandStatus bits = cmdBitCache.get(id);
        InfraredCommandStatus words = cmdWordCache.get(id);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] InfraredCommand#{} Snapshot 差距過大：{}ms", id, delta);
            return;
        }

        InfraredCommandStatus combined = new InfraredCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setInfraredId(id);

        InfraredCommandStatus previous = commandStatusCache.getLatest(id);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new InfraredCommandUpdatedEvent(id, combined));
        }
        commandStatusCache.put(id, combined);
        cmdBitCache.remove(id);
        cmdWordCache.remove(id);
    }

    private boolean isWriteBitArea(PlcInfraredProperties.Infrared device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    private boolean isWriteWordArea(PlcInfraredProperties.Infrared device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    private PlcInfraredProperties.Infrared findDeviceById(int id) {
        return properties.getInfrareds().stream()
                .filter(d -> d.getId() == id)
                .findFirst().orElse(null);
    }

    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<InfraredStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            //eventPublisher.publishInfraredStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Infrared startDevicePushTask", () -> eventPublisher.publishInfraredStatusUpdatedBatch(batch));
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<InfraredCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            //eventPublisher.publishInfraredCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Infrared startCommandPushTask", () -> eventPublisher.publishInfraredCommandUpdatedBatch(batch));
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
            for (Map.Entry<Integer, InfraredStateMachine> entry : stateMachines.entrySet()) {
                int id = entry.getKey();
                InfraredDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    eventPublisher.publishInfraredStatusOverdue(new InfraredStatusOverdueEvent(id, latest));
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcInfraredProperties.Infrared device : properties.getInfrareds()) {
                int id = device.getId();
                InfraredCommandStatus status = commandStatusCache.getLatest(id);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    eventPublisher.publishInfraredCommandOverdue(new InfraredCommandOverdueEvent(id, status));
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

    public InfraredCommandStatus getLatestCommandStatus(int id) {
        return commandStatusCache.getLatest(id);
    }
}
