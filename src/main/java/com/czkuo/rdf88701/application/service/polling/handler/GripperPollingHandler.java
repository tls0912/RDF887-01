package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcGripperProperties;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.gripper.GripperStateMachine;
import com.czkuo.rdf88701.infra.cache.GripperCommandCache;
import com.czkuo.rdf88701.infra.cache.GripperStatusCache;
import com.czkuo.rdf88701.infra.decoder.GripperCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.GripperDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.gripper.GripperStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.GripperMemoryLayoutService;
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
 * GripperPollingHandler
 * - 處理 Gripper 輪詢資料（Bit / Word）
 * - 同時支援狀態（DeviceStatus）與控制命令（CommandStatus）
 * - 負責資料合併、狀態機推進、事件快取與推播、過期監控
 *
 * 2026-06-24 狀態：已檢查，註解與現有實作相符。
 *
 * 2026-06-24 ver B 狀態：已檢查，// 註解與現有實作相符。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GripperPollingHandler implements PollingHandler {

    // === 注入元件 ===
    private final PlcGripperProperties gripperProperties;
    private final GripperDataDecoder gripperDataDecoder;
    private final GripperCommandDecoder gripperCommandDecoder;
    private final GripperMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher plcEventPublisher;
    private final GripperStatusCache statusCache;
    private final GripperCommandCache commandCache;

    // === 狀態與命令快取 ===
    private final Map<Integer, GripperStateMachine> stateMachines = new ConcurrentHashMap<>();
    private final Map<Integer, GripperDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, GripperDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, GripperCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, GripperCommandStatus> cmdWordCache = new ConcurrentHashMap<>();

    // === 推播事件快取區 ===
    private final List<GripperStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<GripperCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    // === 排程器與任務 ===
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> monitorTask;
    private ScheduledFuture<?> pushTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;

    /**
     * 初始化所有 Gripper 狀態機（啟動時呼叫）
     */
    public void initGripperStateMachines() {
        for (PlcGripperProperties.Gripper gripper : gripperProperties.getGrippers()) {
            stateMachines.put(gripper.getId(), new GripperStateMachine(gripper.getId()));
        }
        log.info("[INIT] Gripper 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    /**
     * 處理 PLC Bit 區資料
     */
    @Override
    public void handleBitData(int gripperId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcGripperProperties.Gripper gripper = findGripperById(gripperId);
        if (gripper == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;

        if (isWriteBitArea(gripper, fullStartAddress, end)) {
            // ⬅ 寫入區 → 視為指令資料（Command）
            byte[] actual = memoryLayoutService.extractAreaBytes(gripperId, "write", "B", fullData, fullStartAddress);
            GripperCommandStatus cmd = new GripperCommandStatus();
            gripperCommandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(gripperId, cmd);
            tryCombineCommand(gripperId);
        } else {
            // ⬅ 讀取區 → 狀態資料（DeviceStatus）
            byte[] actual = memoryLayoutService.extractAreaBytes(gripperId, "read", "B", fullData, fullStartAddress);
            GripperDeviceStatus status = new GripperDeviceStatus();
            gripperDataDecoder.decodeBits(actual, status);
            status.setAvailable(true);
            status.setSnapshotTime(snapshotTime);
            bitCache.put(gripperId, status);
            tryCombine(gripperId);
        }
    }

    /**
     * 處理 PLC Word 區資料
     */
    @Override
    public void handleWordData(int gripperId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcGripperProperties.Gripper gripper = findGripperById(gripperId);
        if (gripper == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;

        if (isWriteWordArea(gripper, fullStartAddress, end)) {
            // ⬅ 寫入區 → 指令資料
            byte[] actual = memoryLayoutService.extractAreaBytes(gripperId, "write", "W", fullData, fullStartAddress);
            GripperCommandStatus cmd = new GripperCommandStatus();
            gripperCommandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(gripperId, cmd);
            tryCombineCommand(gripperId);
        } else {
            // ⬅ 讀取區 → 狀態資料
            byte[] actual = memoryLayoutService.extractAreaBytes(gripperId, "read", "W", fullData, fullStartAddress);
            GripperDeviceStatus status = new GripperDeviceStatus();
            gripperDataDecoder.decodeWords(actual, status);
            status.setAvailable(true);
            status.setSnapshotTime(snapshotTime);
            wordCache.put(gripperId, status);
            tryCombine(gripperId);
        }
    }

    /**
     * 嘗試合併 Bit + Word 為完整狀態，並推進狀態機
     */
    private void tryCombine(int gripperId) {
        GripperDeviceStatus bits = bitCache.get(gripperId);
        GripperDeviceStatus words = wordCache.get(gripperId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Gripper#{} Snapshot 差距過大：{}ms", gripperId, delta);
            return;
        }

        GripperDeviceStatus combined = new GripperDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setGripperId(gripperId);

        GripperStateMachine machine = stateMachines.get(gripperId);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            GripperStatusUpdatedEvent event = new GripperStatusUpdatedEvent(
                    gripperId, combined, machine.getCurrentState()
            );
            pendingEvents.add(event);
        }
        statusCache.put("Gripper#" + gripperId, combined);
        bitCache.remove(gripperId);
        wordCache.remove(gripperId);
    }

    /**
     * 嘗試合併 Bit + Word 為完整指令，並檢查是否需要推播
     */
    private void tryCombineCommand(int gripperId) {
        GripperCommandStatus bits = cmdBitCache.get(gripperId);
        GripperCommandStatus words = cmdWordCache.get(gripperId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] GripperCommand#{} Snapshot 差距過大：{}ms", gripperId, delta);
            return;
        }

        GripperCommandStatus combined = new GripperCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setSnapshotTime(Instant.now());
        combined.setComplete(true);

        GripperCommandStatus previous = commandCache.getLatest(gripperId);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new GripperCommandUpdatedEvent(gripperId, combined));
        }

        commandCache.put(gripperId, combined);
        cmdBitCache.remove(gripperId);
        cmdWordCache.remove(gripperId);
    }

    /**
     * 啟動資料過期檢查（狀態）
     */
    public void startDeviceMonitoring() {
        monitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Integer, GripperStateMachine> entry : stateMachines.entrySet()) {
                int id = entry.getKey();
                GripperDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    plcEventPublisher.publishGripperStatusOverdue(new GripperStatusOverdueEvent(id, latest));
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * 啟動資料過期檢查（Command）
     */
    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcGripperProperties.Gripper gripper : gripperProperties.getGrippers()) {
                int id = gripper.getId();
                GripperCommandStatus status = commandCache.getLatest(id);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    plcEventPublisher.publishGripperCommandOverdue(new GripperCommandOverdueEvent(id, status));
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * 啟動狀態推播任務（每秒推一次）
     */
    public void startDevicePushTask() {
        pushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<GripperStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            //plcEventPublisher.publishGripperStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Gripper startDevicePushTask", () -> plcEventPublisher.publishGripperStatusUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 啟動指令推播任務（每秒推一次）
     */
    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<GripperCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            //plcEventPublisher.publishGripperCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Gripper startCommandPushTask", () -> plcEventPublisher.publishGripperCommandUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    public void stopDeviceMonitoring() {
        if (monitorTask != null) monitorTask.cancel(true);
    }

    public void stopCommandMonitoring() {
        if (commandMonitorTask != null) commandMonitorTask.cancel(true);
    }

    public void stopDevicePushTask() {
        if (pushTask != null) pushTask.cancel(true);
    }

    public void stopCommandPushTask() {
        if (commandPushTask != null) commandPushTask.cancel(true);
    }

    // === 查詢與區段判斷 ===

    private PlcGripperProperties.Gripper findGripperById(int id) {
        return gripperProperties.getGrippers().stream()
                .filter(g -> g.getId() == id)
                .findFirst().orElse(null);
    }

    private boolean isWriteBitArea(PlcGripperProperties.Gripper device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    private boolean isWriteWordArea(PlcGripperProperties.Gripper device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }
}
