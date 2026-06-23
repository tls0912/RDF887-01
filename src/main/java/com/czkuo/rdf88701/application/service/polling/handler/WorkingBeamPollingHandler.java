package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcWorkingBeamProperties;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.workingbeam.WorkingBeamStateMachine;
import com.czkuo.rdf88701.infra.cache.WorkingBeamCommandCache;
import com.czkuo.rdf88701.infra.cache.WorkingBeamStatusCache;
import com.czkuo.rdf88701.infra.decoder.WorkingBeamCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.WorkingBeamDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.workingbeam.WorkingBeamStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.WorkingBeamMemoryLayoutService;
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
 * WorkingBeamPollingHandler
 * - 處理 PLC 資料輪詢解析與事件推播邏輯。
 * - 支援 Bit/Word 資料解析與快取，並合併成完整裝置狀態。
 * - 包含 PLC 寫入指令區（Command）與讀取區（DeviceStatus）的區分。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingBeamPollingHandler implements PollingHandler {

    private final PlcWorkingBeamProperties properties;
    private final WorkingBeamDataDecoder dataDecoder;
    private final WorkingBeamCommandDecoder commandDecoder;
    private final WorkingBeamMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;
    private final WorkingBeamStatusCache statusCache;
    private final WorkingBeamCommandCache commandStatusCache;

    private final Map<Integer, WorkingBeamDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, WorkingBeamDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, WorkingBeamCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, WorkingBeamCommandStatus> cmdWordCache = new ConcurrentHashMap<>();
    private final Map<Integer, WorkingBeamStateMachine> stateMachines = new ConcurrentHashMap<>();

    private final List<WorkingBeamStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<WorkingBeamCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;

    // 初始化所有 WorkingBeam 狀態機
    public void initWorkingBeamStateMachines() {
        for (PlcWorkingBeamProperties.WorkingBeam device : properties.getWorkingBeams()) {
            stateMachines.put(device.getId(), new WorkingBeamStateMachine(device.getId()));
        }
        log.info("[INIT] WorkingBeam 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    // 處理 Bit 區資料
    public void handleBitData(int workingBeamId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcWorkingBeamProperties.WorkingBeam device = findDeviceById(workingBeamId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;
        if (isWriteBitArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(workingBeamId, "write", "B", fullData, fullStartAddress);
            WorkingBeamCommandStatus cmd = new WorkingBeamCommandStatus();
            commandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(workingBeamId, cmd);
            tryCombineCommand(workingBeamId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(workingBeamId, "read", "B", fullData, fullStartAddress);
            WorkingBeamDeviceStatus status = new WorkingBeamDeviceStatus();
            dataDecoder.decodeBits(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            bitCache.put(workingBeamId, status);
            tryCombine(workingBeamId);
        }
    }

    // 處理 Word 區資料
    public void handleWordData(int workingBeamId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcWorkingBeamProperties.WorkingBeam device = findDeviceById(workingBeamId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;
        if (isWriteWordArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(workingBeamId, "write", "W", fullData, fullStartAddress);
            WorkingBeamCommandStatus cmd = new WorkingBeamCommandStatus();
            commandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(workingBeamId, cmd);
            tryCombineCommand(workingBeamId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(workingBeamId, "read", "W", fullData, fullStartAddress);
            WorkingBeamDeviceStatus status = new WorkingBeamDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            wordCache.put(workingBeamId, status);
            tryCombine(workingBeamId);
        }
    }

    // 合併裝置 Bit + Word 狀態為完整狀態
    private void tryCombine(int workingBeamId) {
        WorkingBeamDeviceStatus bits = bitCache.get(workingBeamId);
        WorkingBeamDeviceStatus words = wordCache.get(workingBeamId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] WorkingBeam#{} Snapshot 差距過大：{}ms", workingBeamId, delta);
            return;
        }

        WorkingBeamDeviceStatus combined = new WorkingBeamDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setWorkingBeamId(workingBeamId);

        WorkingBeamStateMachine machine = stateMachines.get(workingBeamId);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            WorkingBeamStatusUpdatedEvent event = new WorkingBeamStatusUpdatedEvent(workingBeamId, combined, machine.getCurrentState());
            pendingEvents.add(event);
        }
        statusCache.put("WorkingBeam#" + workingBeamId, combined);

        bitCache.remove(workingBeamId);
        wordCache.remove(workingBeamId);
    }

    // 合併 Command Bit + Word 狀態為完整狀態
    private void tryCombineCommand(int workingBeamId) {
        WorkingBeamCommandStatus bits = cmdBitCache.get(workingBeamId);
        WorkingBeamCommandStatus words = cmdWordCache.get(workingBeamId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] WorkingBeamCommand#{} Snapshot 差距過大：{}ms", workingBeamId, delta);
            return;
        }

        WorkingBeamCommandStatus combined = new WorkingBeamCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setWorkingBeamId(workingBeamId);

        WorkingBeamCommandStatus previous = commandStatusCache.getLatest(workingBeamId);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new WorkingBeamCommandUpdatedEvent(workingBeamId, combined));
        }
        commandStatusCache.put(workingBeamId, combined);

        cmdBitCache.remove(workingBeamId);
        cmdWordCache.remove(workingBeamId);
    }

    // 判斷是否為寫入區（Bit）
    private boolean isWriteBitArea(PlcWorkingBeamProperties.WorkingBeam device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B")
                        && pollStart <= a.getAddress() + a.getLength() - 1
                        && pollEnd >= a.getAddress());
    }

    // 判斷是否為寫入區（Word）
    private boolean isWriteWordArea(PlcWorkingBeamProperties.WorkingBeam device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W")
                        && pollStart <= a.getAddress() + a.getLength() - 1
                        && pollEnd >= a.getAddress());
    }

    // 根據 workingBeamId 找對應設定
    private PlcWorkingBeamProperties.WorkingBeam findDeviceById(int id) {
        return properties.getWorkingBeams().stream().filter(d -> d.getId() == id).findFirst().orElse(null);
    }

    // 啟動狀態推播任務
    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<WorkingBeamStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            //eventPublisher.publishWorkingBeamStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] WorkingBeam startDevicePushTask", () -> eventPublisher.publishWorkingBeamStatusUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    // 啟動命令推播任務
    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<WorkingBeamCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            eventPublisher.publishWorkingBeamCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] WorkingBeam startCommandPushTask", () -> eventPublisher.publishWorkingBeamCommandUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    public void stopDevicePushTask() {
        if (devicePushTask != null) devicePushTask.cancel(true);
    }

    public void stopCommandPushTask() {
        if (commandPushTask != null) commandPushTask.cancel(true);
    }

    public void startDeviceMonitoring() {
        deviceMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Integer, WorkingBeamStateMachine> entry : stateMachines.entrySet()) {
                int workingBeamId = entry.getKey();
                WorkingBeamDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    WorkingBeamStatusOverdueEvent event = new WorkingBeamStatusOverdueEvent(workingBeamId, latest);
                    eventPublisher.publishWorkingBeamStatusOverdue(event);
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcWorkingBeamProperties.WorkingBeam beam : properties.getWorkingBeams()) {
                int workingBeamId = beam.getId();
                WorkingBeamCommandStatus status = commandStatusCache.getLatest(workingBeamId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    WorkingBeamCommandOverdueEvent event = new WorkingBeamCommandOverdueEvent(workingBeamId, status);
                    eventPublisher.publishWorkingBeamCommandOverdue(event);
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

    public WorkingBeamCommandStatus getLatestCommandStatus(int id) {
        return commandStatusCache.getLatest(id);
    }
}
