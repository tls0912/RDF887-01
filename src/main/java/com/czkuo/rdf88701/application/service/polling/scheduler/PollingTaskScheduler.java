package com.czkuo.rdf88701.application.service.polling.scheduler;

import com.czkuo.rdf88701.application.interfaces.PlcSafeAccess;
import com.czkuo.rdf88701.application.service.polling.PollingDataRouter;
import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.PlcProperties;
import com.czkuo.rdf88701.domain.plc.strategy.PlcPollingTuner;
import com.czkuo.rdf88701.infra.adapter.plc.connection.PlcClientManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * PollingTaskScheduler
 * - 控制每個 PLC 裝置的輪詢任務生命週期（啟動、停止、重試、恢復）
 * - 同時監控 Read 與 Write 區域
 * - 與 PollingDataRouter 結合，將輪詢結果導入對應的資料處理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PollingTaskScheduler {

    private final PlcProperties plcProperties;
    private final PlcClientManager plcClientManager;
    private final PlcPollingTuner pollingTuner;
    private final PlcSafeAccess plcSafeAccess;
    private final PollingDataRouter pollingDataRouter;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final ExecutorService ioPool = Executors.newFixedThreadPool(1); // 控制併發
    private final Map<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
    private static final int MAX_RETRY_COUNT = 3;
    private static final int MAX_READ_BYTES = 2048; // 👉 視 PLC 調整 (常見 256 / 512 / 960)
    private static final int MERGE_GAP = 2;        // 👉 容忍 address 小斷點

    public void startAllPollingTasks() {
        List<PlcProperties.Device> devices = plcProperties.getDevices();
        for (PlcProperties.Device device : devices) {
            if (!device.isEnabled() || !device.isDefaultPollingEnabled()) continue;
            startPolling(device.getName());
        }
        scheduler.schedule(this::retryUninitializedDevices, 5, TimeUnit.SECONDS);
    }

    public void stopAllPollingTasks() {
        taskMap.values().forEach(future -> future.cancel(true));
        scheduler.shutdown();
        log.info("[POLL] 所有輪詢任務已停止");
    }

    public void stopPolling(String deviceName) {
        ScheduledFuture<?> future = taskMap.remove(deviceName);
        if (future != null) {
            future.cancel(false);
            log.info("[POLL] 裝置 '{}' 輪詢已停止", deviceName);
        }
    }

    public void resumePolling(String deviceName) {
        PlcProperties.Device device = plcProperties.getDevices().stream()
                .filter(d -> d.getName().equals(deviceName))
                .findFirst().orElse(null);
        if (device == null || !device.isEnabled() || !device.isDefaultPollingEnabled()) {
            log.warn("[POLL] 裝置 '{}' 無法恢復輪詢（未啟用或不存在）", deviceName);
            return;
        }

        if (!plcClientManager.isInitialized(deviceName) || !plcClientManager.isActuallyConnected(deviceName)) {
            log.warn("[POLL] 裝置 '{}' 尚未連線，略過恢復輪詢", deviceName);
            return;
        }

        startPolling(deviceName);
        log.info("[POLL] 裝置 '{}' 已恢復輪詢", deviceName);
    }

    private void startPolling(String deviceName) {
        long interval = pollingTuner.getAdjustedInterval(deviceName, plcProperties.getPollInterval());
        ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
            boolean success = true;
            if (!plcClientManager.isActuallyConnected(deviceName)) {
                log.warn("[POLL] 裝置 '{}' 已失去連線，停止輪詢", deviceName);
                stopPolling(deviceName);
                return;
            }

            try {
                PlcProperties.Device device = plcProperties.getDevices().stream()
                        .filter(d -> d.getName().equals(deviceName))
                        .findFirst().orElse(null);

                if (device == null) return;

                Instant snapshotTime = Instant.now(); // 同一輪排程統一 snapshotTime
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                futures.addAll(dispatchPollingTasks(deviceName, device.getReadAreas(), "READ", snapshotTime));
                futures.addAll(dispatchPollingTasks(deviceName, device.getWriteAreas(), "WRITE", snapshotTime));

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception ex) {
                success = false;
                log.error("[POLL] 輪詢 '{}' 失敗：{}", deviceName, ex.getMessage(), ex);
                stopPolling(deviceName);
            }

            if (success) {
                pollingTuner.recordSuccess(deviceName);
            } else {
                pollingTuner.recordFailure(deviceName);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        taskMap.put(deviceName, future);
    }

    private List<List<PlcProperties.AddressRange>> mergeContinuousAreas(List<PlcProperties.AddressRange> areas) {
        List<List<PlcProperties.AddressRange>> result = new ArrayList<>();
        if (areas == null || areas.isEmpty())
            return result;
        //  先按 areaType 分組（避免 D 跟 M 混）
        Map<String, List<PlcProperties.AddressRange>> typeGroup =
                areas.stream().collect(Collectors.groupingBy(PlcProperties.AddressRange::getAreaType));
        for (List<PlcProperties.AddressRange> group : typeGroup.values()) {
            group.sort(Comparator.comparingInt(PlcProperties.AddressRange::getStart));
            List<PlcProperties.AddressRange> current = new ArrayList<>();
            PlcProperties.AddressRange prev = null;
            for (PlcProperties.AddressRange area : group) {
                if (prev == null) {
                    current.add(area);
                } else {
                    int prevEnd = prev.getStart() + prev.getLength();

                    //  gap 容忍（關鍵優化）
                    if (area.getStart() <= prevEnd + MERGE_GAP) {
                        current.add(area);
                    } else {
                        result.add(current);
                        current = new ArrayList<>();
                        current.add(area);
                    }
                }
                prev = area;
            }
            result.add(current);
        }
        return result;
    }

    private List<int[]> splitByMaxLength(String areaType, int start, int totalComponent) {

        List<int[]> result = new ArrayList<>();
        int maxComponent = getMaxComponentPerRead(areaType);
        int currentStart = start;
        int remaining = totalComponent;
        while (remaining > 0) {
            int take = Math.min(remaining, maxComponent);
            result.add(new int[]{currentStart, take});
            currentStart += take;
            remaining -= take;
        }
        return result;
    }

    private void pollMergedArea(String deviceName, List<PlcProperties.AddressRange> areas, String tag, Instant snapshotTime) {
        PlcProperties.AddressRange first = areas.get(0);
        String areaType = first.getAreaType();
        int start = first.getStart();
        int end = start;
        for (PlcProperties.AddressRange a : areas) {
            int aEnd = a.getStart() + a.getLength();
            if (aEnd > end) end = aEnd;
        }
        int totalComponent = end - start;
        //  切片（避免 PLC 爆）
        List<int[]> segments = splitByMaxLength(areaType, start, totalComponent);
        for (int[] seg : segments) {

            int segStart = seg[0];
            int segComponent = seg[1];
            int byteLen = PlcAddressUtils.calculateByteLengthByType(areaType, segComponent);
            String address = areaType + PlcAddressUtils.formatAddressHexWithout0x(segStart);
            byte[] mergedData = plcSafeAccess.readBytes(deviceName, address, byteLen);
            if (mergedData.length < byteLen) {
                log.error("[POLL] read length mismatch expected={} actual={}", byteLen, mergedData.length);
                continue; // 或 continue
            }
            //log.debug("[POLL-MERGED] {}:{} @{} [{} Bytes]", tag, deviceName, address, mergedData.length);
            //  分發回原本 area
            for (PlcProperties.AddressRange area : areas) {
                int areaStart = area.getStart();
                int areaEnd = areaStart + area.getLength();
                int segEnd = segStart + segComponent;
                // ❗ 不在此 segment 範圍 → skip
                if (areaEnd <= segStart || areaStart >= segEnd)
                    continue;
                int overlapStart = Math.max(areaStart, segStart);
                int overlapEnd = Math.min(areaEnd, segEnd);
                int componentOffset = overlapStart - segStart;
                int componentLen = overlapEnd - overlapStart;
                int byteOffset = PlcAddressUtils.calculateByteLengthByType(areaType, componentOffset);
                int sliceLen = PlcAddressUtils.calculateByteLengthByType(areaType, componentLen);
                byte[] slice = Arrays.copyOfRange(mergedData, byteOffset, byteOffset + sliceLen);
                pollingDataRouter.route(deviceName, tag, area.getAreaType(), overlapStart, slice, snapshotTime);
            }
        }
    }

    private List<CompletableFuture<Void>> dispatchPollingTasks(String deviceName,
                                                               List<PlcProperties.AddressRange> areas,
                                                               String type, Instant snapshotTime) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        if (areas == null)
            return futures;
        if (((ThreadPoolExecutor) ioPool).getQueue().size() > 100) {
            ThreadPoolExecutor executor = (ThreadPoolExecutor) ioPool;
            log.warn("[POLL] IO backlog too large (queue={}) device={} type={}", executor.getQueue().size(), deviceName, type);
            return futures;
        }
//        List<List<PlcProperties.AddressRange>> groups = mergeContinuousAreas(areas);
//        //log.debug("areas={}, mergedGroups={}", areas.size(), groups.size());
//        for (List<PlcProperties.AddressRange> group : groups) {
//            futures.add(
//                    CompletableFuture.runAsync(() -> {
//                        try {
//                            pollMergedArea(deviceName, group, type, snapshotTime);
//                        } catch (Exception e) {
//                            log.error("[Polling] merged area error device={} group={}", deviceName, group, e);
//                        }
//                    }, ioPool)
//            );
//        }
//不合併讀取的版本
        for (PlcProperties.AddressRange area : areas) {
            futures.add(
                    CompletableFuture.runAsync(() -> {
                        try {
                            pollArea(deviceName, area, type, snapshotTime);
                        } catch (Exception e) {
                            log.error("[Polling] area error device={} area={}", deviceName, area, e);
                        }
                    }, ioPool)
            );
        }

        return futures;
    }

    private int getMaxComponentPerRead(String areaType) {
        return switch (areaType.toUpperCase()) {
            case "W", "D", "R", "Z" -> Math.max(1, MAX_READ_BYTES / 2);
            case "B", "M", "X", "Y" -> Math.max(1, MAX_READ_BYTES * 8 - 7);
            default -> throw new IllegalArgumentException("Unsupported PLC area type: " + areaType);
        };
    }

    private void pollArea(String deviceName, PlcProperties.AddressRange area, String tag, Instant snapshotTime) {
        int componentCount = area.getLength();
        String address = area.getAreaType() + PlcAddressUtils.formatAddressHexWithout0x(area.getStart());
        int byteLength = PlcAddressUtils.calculateByteLengthByType(area.getAreaType(), componentCount);
        byte[] fullData = plcSafeAccess.readBytes(deviceName, address, byteLength);

        //log.debug("[POLL] {}:{} @{} [{} Bytes]", tag, deviceName, address, fullData.length);
        pollingDataRouter.route(deviceName, tag, area.getAreaType(), area.getStart(), fullData, snapshotTime);
    }

    private void retryUninitializedDevices() {
        boolean needRetry = false;
        for (PlcProperties.Device device : plcProperties.getDevices()) {
            if (!device.isEnabled() || !device.isDefaultPollingEnabled()) continue;
            String deviceName = device.getName();

            if (taskMap.containsKey(deviceName)) continue;

            if (plcClientManager.isInitialized(deviceName) && plcClientManager.isActuallyConnected(deviceName)) {
                log.info("[POLL] 裝置 '{}' 初始化成功，補啟輪詢", deviceName);
                startPolling(deviceName);
                retryCountMap.remove(deviceName);
            } else {
                int count = retryCountMap.getOrDefault(deviceName, 0);
                if (count < MAX_RETRY_COUNT) {
                    retryCountMap.put(deviceName, count + 1);
                    log.warn("[POLL] 裝置 '{}' 初始化失敗（重試 {} 次）", deviceName, count + 1);
                    needRetry = true;
                } else {
                    log.error("[POLL] 裝置 '{}' 重試超過上限（{}次）", deviceName, MAX_RETRY_COUNT);
                }
            }
        }

        if (needRetry) {
            scheduler.schedule(this::retryUninitializedDevices, 5, TimeUnit.SECONDS);
        }
    }
}
