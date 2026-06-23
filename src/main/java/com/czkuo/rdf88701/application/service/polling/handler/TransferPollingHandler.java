package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcTransferProperties;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferDeviceStatus;
import com.czkuo.rdf88701.domain.plc.state.transfer.TransferStateMachine;
import com.czkuo.rdf88701.infra.cache.TransferCommandCache;
import com.czkuo.rdf88701.infra.cache.TransferStatusCache;
import com.czkuo.rdf88701.infra.decoder.TransferCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.TransferDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.transfer.TransferStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.TransferMemoryLayoutService;
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
 * TransferPollingHandler
 * - 處理 PLC 傳送設備（Transfer）的輪詢資料
 * - 支援 Bit / Word 資料區解析與合併
 * - 分辨 Read/Write 區域，將其解碼為狀態 / 控制命令
 * - 合併完整狀態後推送至事件中心
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransferPollingHandler implements PollingHandler {

    // === 注入元件 ===
    private final PlcTransferProperties properties;
    private final TransferDataDecoder dataDecoder;
    private final TransferCommandDecoder commandDecoder;
    private final TransferMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;
    private final TransferStatusCache statusCache;
    private final TransferCommandCache commandStatusCache;

    // 暫存區（等待 Bit + Word 合併）
    private final Map<Integer, TransferDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, TransferDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, TransferCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, TransferCommandStatus> cmdWordCache = new ConcurrentHashMap<>();
    private final Map<Integer, TransferStateMachine> stateMachines = new ConcurrentHashMap<>();

    // 等待批次推播的事件暫存區
    private final List<TransferStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<TransferCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    // 定時排程器（推播 + 過期偵測）
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;
    /**
     * 初始化 Transfer 狀態機（啟動時呼叫）
     */
    public void initTransferStateMachines() {
        for (PlcTransferProperties.Transfer device : properties.getTransfers()) {
            stateMachines.put(device.getId(), new TransferStateMachine(device.getId()));
        }
        log.info("[INIT] Transfer 狀態機初始化完成，共 {} 台", stateMachines.size());
    }

    /**
     * 處理 Bit 資料區
     */
    public void handleBitData(int transferId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcTransferProperties.Transfer device = findDeviceById(transferId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;

        if (isWriteBitArea(device, fullStartAddress, end)) {
            // 解碼指令區（Write Area）
            byte[] actual = memoryLayoutService.extractAreaBytes(transferId, "write", "B", fullData, fullStartAddress);
            TransferCommandStatus cmd = new TransferCommandStatus();
            commandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(transferId, cmd);
            tryCombineCommand(transferId);
        } else {
            // 解碼狀態區（Read Area）
            byte[] actual = memoryLayoutService.extractAreaBytes(transferId, "read", "B", fullData, fullStartAddress);
            TransferDeviceStatus status = new TransferDeviceStatus();
            dataDecoder.decodeBits(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            bitCache.put(transferId, status);
            tryCombine(transferId);
        }
    }

    /**
     * 處理 Word 資料區
     */
    public void handleWordData(int transferId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcTransferProperties.Transfer device = findDeviceById(transferId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;

        if (isWriteWordArea(device, fullStartAddress, end)) {
            // 解碼指令區
            byte[] actual = memoryLayoutService.extractAreaBytes(transferId, "write", "W", fullData, fullStartAddress);
            TransferCommandStatus cmd = new TransferCommandStatus();
            commandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(transferId, cmd);
            tryCombineCommand(transferId);
        } else {
            // 解碼狀態區
            byte[] actual = memoryLayoutService.extractAreaBytes(transferId, "read", "W", fullData, fullStartAddress);
            TransferDeviceStatus status = new TransferDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            wordCache.put(transferId, status);
            tryCombine(transferId);
        }
    }

    /**
     * 嘗試將 Bit 與 Word 合併為完整 Transfer 裝置狀態
     */
    private void tryCombine(int transferId) {
        TransferDeviceStatus bits = bitCache.get(transferId);
        TransferDeviceStatus words = wordCache.get(transferId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Transfer#{} Snapshot 差距過大：{}ms", transferId, delta);
            return;
        }

        TransferDeviceStatus combined = new TransferDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setTransferId(transferId);

        TransferStateMachine machine = stateMachines.get(transferId);
        if (machine != null && machine.updateFromDeviceStatus(combined)) {
            pendingEvents.add(new TransferStatusUpdatedEvent(transferId, combined, machine.getCurrentState()));
        }
        statusCache.put("Transfer#" + transferId, combined);
        bitCache.remove(transferId);
        wordCache.remove(transferId);
    }

    /**
     * 嘗試將 Bit 與 Word 合併為完整 Transfer 指令狀態
     */
    private void tryCombineCommand(int transferId) {
        TransferCommandStatus bits = cmdBitCache.get(transferId);
        TransferCommandStatus words = cmdWordCache.get(transferId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] TransferCommand#{} Snapshot 差距過大：{}ms", transferId, delta);
            return;
        }

        TransferCommandStatus combined = new TransferCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setTransferId(transferId);

        TransferCommandStatus previous = commandStatusCache.getLatest(transferId);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new TransferCommandUpdatedEvent(transferId, combined));
        }
        commandStatusCache.put(transferId, combined);
        cmdBitCache.remove(transferId);
        cmdWordCache.remove(transferId);
    }

    /**
     * 判斷是否為寫入區（Bit）
     */
    private boolean isWriteBitArea(PlcTransferProperties.Transfer device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    /**
     * 判斷是否為寫入區（Word）
     */
    private boolean isWriteWordArea(PlcTransferProperties.Transfer device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    /**
     * 根據 transferId 找到對應裝置設定
     */
    private PlcTransferProperties.Transfer findDeviceById(int id) {
        return properties.getTransfers().stream()
                .filter(d -> d.getId() == id)
                .findFirst().orElse(null);
    }

    /**
     * 啟動狀態推播任務（每秒一次）
     */
    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<TransferStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            //eventPublisher.publishTransferStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Transfer startDevicePushTask", () -> eventPublisher.publishTransferStatusUpdatedBatch(batch));
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    /**
     * 啟動指令推播任務（每秒一次）
     */
    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<TransferCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            //eventPublisher.publishTransferCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Transfer startCommandPushTask", () -> eventPublisher.publishTransferCommandUpdatedBatch(batch));
        }, 300, 100, TimeUnit.MILLISECONDS);
    }

    public void stopDevicePushTask() {
        if (devicePushTask != null) devicePushTask.cancel(true);
    }

    public void stopCommandPushTask() {
        if (commandPushTask != null) commandPushTask.cancel(true);
    }

    /**
     * 啟動狀態過期檢查任務（每 5 秒）
     */
    public void startDeviceMonitoring() {
        deviceMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (Map.Entry<Integer, TransferStateMachine> entry : stateMachines.entrySet()) {
                int transferId = entry.getKey();
                TransferDeviceStatus latest = entry.getValue().getLatestDeviceStatus();
                if (latest != null && latest.isOverdue(30)) {
                    latest.setStale(true);
                    TransferStatusOverdueEvent event = new TransferStatusOverdueEvent(transferId, latest);
                    eventPublisher.publishTransferStatusOverdue(event);
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * 啟動指令過期檢查任務（每 5 秒）
     */
    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcTransferProperties.Transfer transfer : properties.getTransfers()) {
                int transferId = transfer.getId();
                TransferCommandStatus status = commandStatusCache.getLatest(transferId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    TransferCommandOverdueEvent event = new TransferCommandOverdueEvent(transferId, status);
                    eventPublisher.publishTransferCommandOverdue(event);
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

    /**
     * 查詢最新 Transfer 指令狀態
     */
    public TransferCommandStatus getLatestCommandStatus(int id) {
        return commandStatusCache.getLatest(id);
    }
}
