package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.MonitorPoolDispatcher;
import com.czkuo.rdf88701.config.plc.PlcSiteProperties;
import com.czkuo.rdf88701.domain.plc.state.site.SiteCommandStatus;
import com.czkuo.rdf88701.domain.plc.state.site.SiteDeviceStatus;
import com.czkuo.rdf88701.infra.cache.SiteCommandCache;
import com.czkuo.rdf88701.infra.cache.SiteStatusCache;
import com.czkuo.rdf88701.infra.decoder.SiteCommandDecoder;
import com.czkuo.rdf88701.infra.decoder.SiteDataDecoder;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteCommandOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteCommandUpdatedEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.site.SiteStatusUpdatedEvent;
import com.czkuo.rdf88701.infra.service.SiteMemoryLayoutService;
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
 * SitePollingHandler
 * - 處理 PLC Site Bit/Word 資料解析與狀態推播邏輯。
 * - 每個 Site 資料不需狀態機，也不分 command 與 status 區，僅解析並快取最新狀態。
 * - 輪詢資料來源由 PLC Polling 中央服務調用本類別進行處理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SitePollingHandler implements PollingHandler {

    // === 注入元件 ===
    private final PlcSiteProperties properties;
    private final SiteDataDecoder dataDecoder;
    private final SiteCommandDecoder commandDecoder;
    private final SiteMemoryLayoutService memoryLayoutService;
    private final PlcEventPublisher eventPublisher;

    private final SiteStatusCache statusCache;
    private final SiteCommandCache commandCache;

    // === 快取資料（等待合併）===
    private final Map<Integer, SiteDeviceStatus> bitCache = new ConcurrentHashMap<>();
    private final Map<Integer, SiteDeviceStatus> wordCache = new ConcurrentHashMap<>();
    private final Map<Integer, SiteCommandStatus> cmdBitCache = new ConcurrentHashMap<>();
    private final Map<Integer, SiteCommandStatus> cmdWordCache = new ConcurrentHashMap<>();

    // === 推播暫存 ===
    private final List<SiteStatusUpdatedEvent> pendingDeviceEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<SiteCommandUpdatedEvent> pendingCommandEvents = Collections.synchronizedList(new ArrayList<>());

    // === 排程任務 ===
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private ScheduledFuture<?> deviceMonitorTask;
    private ScheduledFuture<?> commandMonitorTask;
    private ScheduledFuture<?> devicePushTask;
    private ScheduledFuture<?> commandPushTask;
    @Resource
    private MonitorPoolDispatcher dispatcher;

    /**
     * 處理 Bit 資料區
     */
    public void handleBitData(int siteId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcSiteProperties.Site device = findDeviceById(siteId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("B", fullData.length) - 1;

        if (isWriteBitArea(device, fullStartAddress, end)) {
            // 解碼指令區（Write Area）
            byte[] actual = memoryLayoutService.extractAreaBytes(siteId, "write", "B", fullData, fullStartAddress);
            SiteCommandStatus cmd = new SiteCommandStatus();
            commandDecoder.decodeBits(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdBitCache.put(siteId, cmd);
            tryCombineCommand(siteId);
        } else {
            // 解碼狀態區（Read Area）
            byte[] actual = memoryLayoutService.extractAreaBytes(siteId, "read", "B", fullData, fullStartAddress);
            SiteDeviceStatus status = new SiteDeviceStatus();
            dataDecoder.decodeBits(actual, siteId, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            bitCache.put(siteId, status);
            tryCombine(siteId);
        }
    }

    /**
     * 處理 Word 資料區
     */
    public void handleWordData(int siteId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcSiteProperties.Site device = findDeviceById(siteId);
        if (device == null) return;

        int end = fullStartAddress + PlcAddressUtils.calculateComponentCountByType("W", fullData.length) - 1;

        if (isWriteWordArea(device, fullStartAddress, end)) {
            // 解碼指令區
            byte[] actual = memoryLayoutService.extractAreaBytes(siteId, "write", "W", fullData, fullStartAddress);
            SiteCommandStatus cmd = new SiteCommandStatus();
            commandDecoder.decodeWords(actual, cmd);
            cmd.setSnapshotTime(snapshotTime);
            cmdWordCache.put(siteId, cmd);
            tryCombineCommand(siteId);
        } else {
            // 解碼狀態區
            byte[] actual = memoryLayoutService.extractAreaBytes(siteId, "read", "W", fullData, fullStartAddress);
            SiteDeviceStatus status = new SiteDeviceStatus();
            dataDecoder.decodeWords(actual, status);
            status.setSnapshotTime(snapshotTime);
            status.setAvailable(true);
            wordCache.put(siteId, status);
            tryCombine(siteId);
        }
    }

    /**
     * 嘗試將 Bit 與 Word 合併為完整 Site 裝置狀態
     */
    private void tryCombine(int siteId) {
        SiteDeviceStatus bits = bitCache.get(siteId);
        SiteDeviceStatus words = wordCache.get(siteId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] Site#{} Snapshot 差距過大：{}ms", siteId, delta);
            return;
        }

        SiteDeviceStatus combined = new SiteDeviceStatus();
        combined.cloneContentFrom(bits, words);
        combined.setAvailable(true);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setSiteId(siteId);

        SiteDeviceStatus previous = statusCache.getLatest("Site#" + siteId);
        if (combined.isContentDifferent(previous)) {
            pendingDeviceEvents.add(new SiteStatusUpdatedEvent(siteId, combined));
        }
        statusCache.put("Site#" + siteId, combined);
        bitCache.remove(siteId);
        wordCache.remove(siteId);
    }

    /**
     * 嘗試將 Bit 與 Word 合併為完整 Site 指令狀態
     */
    private void tryCombineCommand(int siteId) {
        SiteCommandStatus bits = cmdBitCache.get(siteId);
        SiteCommandStatus words = cmdWordCache.get(siteId);
        if (bits == null || words == null) return;

        long delta = Math.abs(bits.getSnapshotTime().toEpochMilli() - words.getSnapshotTime().toEpochMilli());
        if (delta > 100) {
            log.warn("[POLL] SiteCommand#{} Snapshot 差距過大：{}ms", siteId, delta);
            return;
        }

        SiteCommandStatus combined = new SiteCommandStatus();
        combined.cloneContentFrom(bits, words);
        combined.setComplete(true);
        combined.setSnapshotTime(Instant.now());
        combined.setSiteId(siteId);

        SiteCommandStatus previous = commandCache.getLatest(siteId);
        if (combined.hasMeaningfulChange(previous)) {
            pendingCommandEvents.add(new SiteCommandUpdatedEvent(siteId, combined));
        }
        commandCache.put(siteId, combined);
        cmdBitCache.remove(siteId);
        cmdWordCache.remove(siteId);
    }

    /**
     * 判斷是否為寫入區（Bit）
     */
    private boolean isWriteBitArea(PlcSiteProperties.Site device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("B") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    /**
     * 判斷是否為寫入區（Word）
     */
    private boolean isWriteWordArea(PlcSiteProperties.Site device, int pollStart, int pollEnd) {
        return device.getWriteAreas().stream().anyMatch(a ->
                a.getType().equalsIgnoreCase("W") &&
                        pollStart <= a.getAddress() + a.getLength() - 1 &&
                        pollEnd >= a.getAddress());
    }

    /**
     * 根據 transferId 找到對應裝置設定
     */
    private PlcSiteProperties.Site findDeviceById(int id) {
        return properties.getSites().stream()
                .filter(d -> d.getId() == id)
                .findFirst().orElse(null);
    }

    /**
     * 啟動狀態推播任務（每秒一次）
     */
    public void startDevicePushTask() {
        devicePushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<SiteStatusUpdatedEvent> batch;
            synchronized (pendingDeviceEvents) {
                if (pendingDeviceEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingDeviceEvents);
                pendingDeviceEvents.clear();
            }
            //eventPublisher.publishSiteStatusUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Site startDevicePushTask", () -> eventPublisher.publishSiteStatusUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    /**
     * 啟動指令推播任務（每秒一次）
     */
    public void startCommandPushTask() {
        commandPushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<SiteCommandUpdatedEvent> batch;
            synchronized (pendingCommandEvents) {
                if (pendingCommandEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingCommandEvents);
                pendingCommandEvents.clear();
            }
            //eventPublisher.publishSiteCommandUpdatedBatch(batch);
            dispatcher.submit("[dispatcher] Site publishSiteCommandUpdatedBatch", () -> eventPublisher.publishSiteCommandUpdatedBatch(batch));
        }, 300, 50, TimeUnit.MILLISECONDS);
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
            for (PlcSiteProperties.Site site : properties.getSites()) {
                int siteId = site.getId();
                SiteDeviceStatus status = statusCache.getLatest("Site#" + siteId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    SiteStatusOverdueEvent event = new SiteStatusOverdueEvent(siteId, status);
                    eventPublisher.publishSiteStatusOverdue(event);
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /**
     * 啟動指令過期檢查任務（每 5 秒）
     */
    public void startCommandMonitoring() {
        commandMonitorTask = scheduler.scheduleWithFixedDelay(() -> {
            for (PlcSiteProperties.Site site : properties.getSites()) {
                int siteId = site.getId();
                SiteCommandStatus status = commandCache.getLatest(siteId);
                if (status != null && status.isOverdue(30)) {
                    status.setStale(true);
                    SiteCommandOverdueEvent event = new SiteCommandOverdueEvent(siteId, status);
                    eventPublisher.publishSiteCommandOverdue(event);
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
     * 查詢最新 Site 指令狀態
     */
    public SiteCommandStatus getLatestCommandStatus(int id) {
        return commandCache.getLatest(id);
    }
}
