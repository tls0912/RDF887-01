package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcStrappingProperties;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.Strapping.StrappingStateMachine;
import com.czkuo.rdf88701.infra.cache.StrappingCommandCache;
import com.czkuo.rdf88701.infra.cache.StrappingStatusCache;
import com.czkuo.rdf88701.infra.decoder.StrappingCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.StrappingDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.strapping.StrappingStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.StrappingMemoryLayoutService;
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
 * StrappingPollingHandler
 * - 處理 PLC 資料輪詢解析與事件推播邏輯。
 * - 支援 Bit/Word 資料解析與快取，並合併成完整裝置狀態。
 * - 包含 PLC 寫入指令區（Command）與讀取區（DeviceStatus）的區分。
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrappingPollingHandler implements PollingHandler {

    private final PlcStrappingProperties properties;
    private final StrappingDataDecoder dataDecoder;
    private final StrappingCommandDecoder commandDecoder;
    private final StrappingMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;
    private final StrappingStatusCache statusCache;
    private final StrappingCommandCache commandStatusCache;

    private final Map<Integer, StrappingDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, StrappingDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, StrappingCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, StrappingCommandStatus> cmdWordCache = new ConcurrentHashMap<>();
    private final Map<Integer, StrappingStateMachine> stateMachines = new ConcurrentHashMap<>();

    private final List<StrappingStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<StrappingCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;

    /**
     * 初始化所有 Strapping 狀態機
     */
    public void initStrappingStateMachines() {
        for (PlcStrappingProperties.Strapping device : properties.getStrappings()) {
            stateMachines.put(device.getId(), new StrappingStateMachine(device.getId()));
        }
        log.info("[INIT] Strapping 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    /**
     * 處理 Bit 區資料
     */
    public void handleBitData(int strappingId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcStrappingProperties.Strapping device = findDeviceById(strappingId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;
        if (isWriteBitArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(strappingId, "write", "B", fullData, fullStartAddress);
            StrappingCommandStatus cmd = new StrappingCommandStatus();
            commandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(strappingId, cmd);
            tryCombineCommand(strappingId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(strappingId, "read", "B", fullData, fullStartAddress);
            StrappingDeviceStatus status = new StrappingDeviceStatus();
            dataDecoder.decodeBits(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            bitCache.put(strappingId, status);
            tryCombine(strappingId);
        }
    }

    /**
     * 處理 Word 區資料
     */
    public void handleWordData(int strappingId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcStrappingProperties.Strapping device = findDeviceById(strappingId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;
        if (isWriteWordArea(device, fullStartAddress, end)) {
            byte[] actual = memoryLayoutService.extractAreaBytes(strappingId, "write", "W", fullData, fullStartAddress);
            StrappingCommandStatus cmd = new StrappingCommandStatus();
            commandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(strappingId, cmd);
            tryCombineCommand(strappingId);
        } else {
            byte[] actual = memoryLayoutService.extractAreaBytes(strappingId, "read", "W", fullData, fullStartAddress);
            StrappingDeviceStatus status = new StrappingDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            wordCache.put(strappingId, status);
            tryCombine(strappingId);
        }
    }

    /**
     * 合併裝置 Bit + Word 狀態為完整狀態
     */
    private void tryCombine(int strappingId) {
        StrappingDeviceStatus bits = bitCache.get(strappingId);
        StrappingDeviceStatus words = wordCache.get(strappingId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Strapping#{} Snapshot 差距過大：{}ms", strappingId, delta);
            return;
        }

        StrappingDeviceStatus combined = new StrappingDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setStrappingId(strappingId);

        StrappingStateMachine machine = stateMachines.get(strappingId);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            pendingEvents.add(new StrappingStatusUpdatedEvent(strappingId, combined, machine.getCurrentState()));
        }
        statusCache.put("Strapping#" + strappingId, combined);

        bitCache.remove(strappingId);
        wordCache.remove(strappingId);
    }

    /**
     * 合併 Command Bit + Word 狀態為完整狀態
     */
    private void tryCombineCommand(int strappingId) {
        StrappingCommandStatus bits = cmdBitCache.get(strappingId);
        StrappingCommandStatus words = cmdWordCache.get(strappingId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] StrappingCommand#{} Snapshot 差距過大：{}ms", strappingId, delta);
            return;
        }

        StrappingCommandStatus combined = new StrappingCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setStrappingId(strappingId);

        StrappingCommandStatus previous = commandStatusCache.getLatest(strappingId);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new StrappingCommandUpdatedEvent(strappingId, combined));
        }
        commandStatusCache.put(strappingId, combined);

        cmdBitCache.remove(strappingId);
        cmdWordCache.remove(strappingId);
    }

    /**
     * 判斷是否為寫入區（Bit）
     */
    private boolean isWriteBitArea(PlcStrappingProperties.Strapping device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B") && pollStart <= a.getAddress() + a.getLength() - 1 && pollEnd >= a.getAddress());
    }

    /**
     * 判斷是否為寫入區（Word）
     */
    private boolean isWriteWordArea(PlcStrappingProperties.Strapping device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W") && pollStart <= a.getAddress() + a.getLength() - 1 && pollEnd >= a.getAddress());
    }

    /**
     * 根據 strappingId 找對應設定
     */
    private PlcStrappingProperties.Strapping findDeviceById(int id) {
        return properties.getStrappings().stream().filter(d -> d.getId() == id).findFirst().orElse(null);
    }

    /**
     * 啟動狀態推播任務
     */
    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<StrappingStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            eventPublisher.publishStrappingStatusUpdatedBatch(batch);
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 啟動命令推播任務
     */
    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<StrappingCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            eventPublisher.publishStrappingCommandUpdatedBatch(batch);
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    public void stopDevicePushTask() {
        if (devicePushTask != null) devicePushTask.cancel(true);
    }

    public void stopCommandPushTask() {
        if (commandPushTask != null) commandPushTask.cancel(true);
    }

    /**
     * 啟動裝置狀態監控（過期檢查）
     */
    public void startDeviceMonitoring() {
        deviceMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Integer, StrappingStateMachine> entry : stateMachines.entrySet()) {
                int strappingId = entry.getKey();
                StrappingDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    eventPublisher.publishStrappingStatusOverdue(new StrappingStatusOverdueEvent(strappingId, latest));
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * 啟動命令狀態監控（過期檢查）
     */
    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcStrappingProperties.Strapping device : properties.getStrappings()) {
                int strappingId = device.getId();
                StrappingCommandStatus status = commandStatusCache.getLatest(strappingId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    eventPublisher.publishStrappingCommandOverdue(new StrappingCommandOverdueEvent(strappingId, status));
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

    public StrappingCommandStatus getLatestCommandStatus(int id) {
        return commandStatusCache.getLatest(id);
    }
}
