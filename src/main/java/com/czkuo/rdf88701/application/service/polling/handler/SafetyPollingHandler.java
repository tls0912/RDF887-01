package com.czkuo.rdf88701.application.service.polling.handler;

import com.czkuo.rdf88701.common.util.PlcAddressUtils;
import com.czkuo.rdf88701.config.plc.DeviceArea;
import com.czkuo.rdf88701.config.plc.PlcSafetyProperties;
import com.czkuo.rdf88701.config.plc.PlcSafetyRegistry;
import com.czkuo.rdf88701.domain.plc.state.safety.SafetyDeviceStatus;
import com.czkuo.rdf88701.infra.cache.SafetyStatusCache;
import com.czkuo.rdf88701.infra.event.PlcEventPublisher;
import com.czkuo.rdf88701.infra.event.model.plc.safety.SafetyStatusOverdueEvent;
import com.czkuo.rdf88701.infra.event.model.plc.safety.SafetyStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * SafetyPollingHandler
 * - 參考 GripperPollingHandler 的結構，專為「安全感測點」設計
 * - 主要吃 W 區資料（多個連續 word），解碼成 16bits/word 後映射到 Wxxxx.b
 * - 合併變更、推播事件、過期監控
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SafetyPollingHandler implements PollingHandler {

    // === 注入 ===
    private final PlcSafetyProperties safetyProperties;
    private final PlcSafetyRegistry safetyRegistry;
    private final PlcEventPublisher plcEventPublisher;
    private final SafetyStatusCache statusCache;

    // === 快取 ===
    /** 最新狀態快取（deviceId -> snapshot） */
    private final Map<Integer, SafetySnapshot> snapshotCache = new ConcurrentHashMap<>();

    // === 推播批次快取 ===
    private final List<SafetyStatusUpdatedEvent> pendingEvents = Collections.synchronizedList(new ArrayList<>());

    // === 排程器 ===
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> monitorTask;
    private ScheduledFuture<?> pushTask;

    /* ============================================================
     * PollingHandler 介面實作
     * ============================================================ */

    /** 安全感測主要吃 W 區；B 區若沒有配置，可忽略 */
    @Override
    public void handleBitData(int deviceId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        // 多數安全點不使用 B 區；若你未來有 B 區可在此補 decodeBits
    }

    @Override
    public void handleWordData(int deviceId, byte[] fullData, int fullStartAddress, Instant snapshotTime) {
        PlcSafetyProperties.Device device = findDeviceById(deviceId);
        if (device == null) return;

        int components = PlcAddressUtils.calculateComponentCountByType("W", fullData.length); // 2 bytes per W
        int pollEnd = fullStartAddress + components - 1;

        // 把本次輪詢包中的資料，抽出屬於「read-areas 中 type=W」的那幾段，合併成連續 bytes
        byte[] merged = extractAllReadWAreasBytes(device, fullData, fullStartAddress, pollEnd);
        if (merged.length == 0) {
            // 這包不是安全讀區的資料，略過
            return;
        }

        // 以第一段 W 讀區的起始位址當 baseWord（W1040 -> 0x1040）
        Optional<DeviceArea> firstW = device.getReadAreas().stream()
                .filter(a -> "W".equalsIgnoreCase(a.getType()))
                .min(Comparator.comparingInt(DeviceArea::getAddress));
        if (firstW.isEmpty()) return;

        int baseWordHex = firstW.get().getAddress(); // e.g. 0x1040
        // merged 對應的 word 數量
        int totalWords = PlcAddressUtils.calculateComponentCountByType("W", merged.length);

        // 解析每個 word -> 16 bits
        boolean[] allBits = decodeWordsToBits(merged, ByteOrder.BIG_ENDIAN /*依你的 PLC 位元序調整*/);

        // 把 bits 映射到對應的 Wxxxx.b（只針對 enabled 的 points）
        Map<String, Boolean> addrToState = projectBitsToAddrs(device, baseWordHex, totalWords, allBits);

        // 與前一版快照比對 → 有變化才推事件
        SafetySnapshot previous = snapshotCache.get(deviceId);
        SafetySnapshot current = SafetySnapshot.of(deviceId, snapshotTime, addrToState);

        List<SafetyStatusUpdatedEvent> changes = diffToEvents(deviceId, device.getName(), previous, current);
        if (!changes.isEmpty()) {
            pendingEvents.addAll(changes);
        }

        // 更新快取（強型別 SafetyDeviceStatus）
        snapshotCache.put(deviceId, current);
        statusCache.put(device.getName(), toDeviceStatus(deviceId, device.getName(), current));
    }

    /* ============================================================
     * 監控與推播
     * ============================================================ */

    /** 啟動過期監控（每 5 秒檢查一次，逾 30 秒未更新就發 Overdue） */
    public void startDeviceMonitoring() {
        monitorTask = scheduler.scheduleWithFixedDelay(() -> {
            Instant now = Instant.now();
            for (Map.Entry<Integer, SafetySnapshot> e : snapshotCache.entrySet()) {
                SafetySnapshot s = e.getValue();
                if (s != null && DurationSeconds.between(s.snapshotTime, now) > 30) {
                    plcEventPublisher.publishSafetyStatusOverdue(
                            new SafetyStatusOverdueEvent(e.getKey(), s.snapshotTime())
                    );
                }
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    /** 啟動狀態批次推播（每秒推一次） */
    public void startDevicePushTask() {
        pushTask = scheduler.scheduleWithFixedDelay(() -> {
            List<SafetyStatusUpdatedEvent> batch;
            synchronized (pendingEvents) {
                if (pendingEvents.isEmpty()) return;
                batch = new ArrayList<>(pendingEvents);
                pendingEvents.clear();
            }
            plcEventPublisher.publishSafetyStatusUpdatedBatch(batch);
        }, 300, 50, TimeUnit.MILLISECONDS);
    }

    public void stopDeviceMonitoring() {
        if (monitorTask != null) monitorTask.cancel(true);
    }

    public void stopDevicePushTask() {
        if (pushTask != null) pushTask.cancel(true);
    }

    /* ============================================================
     * 私有工具
     * ============================================================ */

    private PlcSafetyProperties.Device findDeviceById(int id) {
        return safetyProperties.getDevices().stream()
                .filter(d -> d.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * 從 fullData 中，擷取屬於 device 的「所有 W 讀區」資料並串接。
     * - 支援單包輪詢覆蓋多個 read-areas 的情況
     * - 若某段不在這包範圍內就跳過（避免拋例外）
     */
    private byte[] extractAllReadWAreasBytes(PlcSafetyProperties.Device device, byte[] fullData, int pollStart, int pollEnd) {
        List<byte[]> segments = new ArrayList<>();
        for (DeviceArea area : device.getReadAreas()) {
            if (!"W".equalsIgnoreCase(area.getType())) continue;
            int areaStart = area.getAddress();
            int areaEnd = areaStart + area.getLength() - 1;

            // 找交集
            int start = Math.max(pollStart, areaStart);
            int end   = Math.min(pollEnd, areaEnd);
            if (end < start) continue; // 無交集

            // 計算 fullData 中的位元組偏移
            // fullData 是以 pollStart 為第一個元件開始的連續資料
            int intersectWords = end - start + 1;
            int byteOffset = PlcAddressUtils.calculateByteOffsetByType("W", start - pollStart);
            int byteLength = PlcAddressUtils.calculateByteLengthByType("W", intersectWords);
            byte[] piece = Arrays.copyOfRange(fullData, byteOffset, byteOffset + byteLength);
            segments.add(piece);
        }
        if (segments.isEmpty()) return new byte[0];

        int totalLen = segments.stream().mapToInt(a -> a.length).sum();
        byte[] merged = new byte[totalLen];
        int pos = 0;
        for (byte[] s : segments) {
            System.arraycopy(s, 0, merged, pos, s.length);
            pos += s.length;
        }
        return merged;
    }

    /** 將連續 words 轉為 bits（每個 word 16 bits） */
    private boolean[] decodeWordsToBits(byte[] wordsBytes, ByteOrder order) {
        int wordCount = PlcAddressUtils.calculateComponentCountByType("W", wordsBytes.length);
        boolean[] bits = new boolean[wordCount * 16];

        ByteBuffer buf = ByteBuffer.wrap(wordsBytes).order(order);
        for (int i = 0; i < wordCount; i++) {
            int unsigned = buf.getShort() & 0xFFFF;
            // 這裡依據 PLC 習慣：bit0 = 最低位
            for (int b = 0; b < 16; b++) {
                bits[i * 16 + b] = ((unsigned >> b) & 0x1) == 1;
            }
        }
        return bits;
    }

    /**
     * 把 bits 投影到 addr（Wxxxx.b），只回傳屬於 device 的 enabled points。
     * baseWordHex: 第一段 W 讀區的起點（例：0x1040）
     */
    private Map<String, Boolean> projectBitsToAddrs(PlcSafetyProperties.Device device,
                                                    int baseWordHex,
                                                    int totalWords,
                                                    boolean[] allBits) {
        // 將 points 建索引（只抓 enabled）
        Map<String, PlcSafetyProperties.Point> enabledIdx = device.getPoints().stream()
                .filter(PlcSafetyProperties.Point::isEnabled)
                .collect(Collectors.toMap(
                        p -> p.getAddr().toUpperCase(Locale.ROOT),
                        p -> p,
                        (a, b) -> a
                ));

        Map<String, Boolean> addrToState = new LinkedHashMap<>();

        for (int w = 0; w < totalWords; w++) {
            String wordStr = "W" + String.format("%04X", baseWordHex + w);
            for (int b = 0; b < 16; b++) {
                String addr = wordStr + "." + Integer.toHexString(b).toUpperCase(Locale.ROOT);
                PlcSafetyProperties.Point p = enabledIdx.get(addr);
                if (p != null) {
                    addrToState.put(addr, allBits[w * 16 + b]);
                }
            }
        }
        return addrToState;
    }

    /** 將差異轉成事件；僅針對「值變化」的點位產生事件 */
    private List<SafetyStatusUpdatedEvent> diffToEvents(int deviceId,
                                                        String deviceName,
                                                        SafetySnapshot prev,
                                                        SafetySnapshot curr) {
        List<SafetyStatusUpdatedEvent> out = new ArrayList<>();
        if (prev == null) {
            // 首次：把所有 enabled 點當作初始事件推一次（或視需求改成不推）
            for (Map.Entry<String, Boolean> e : curr.addrStates.entrySet()) {
                out.add(new SafetyStatusUpdatedEvent(deviceId, deviceName, e.getKey(), e.getValue(), curr.snapshotTime));
            }
            return out;
        }

        for (Map.Entry<String, Boolean> e : curr.addrStates.entrySet()) {
            Boolean oldV = prev.addrStates.get(e.getKey());
            if (oldV == null || !Objects.equals(oldV, e.getValue())) {
                out.add(new SafetyStatusUpdatedEvent(deviceId, deviceName, e.getKey(), e.getValue(), curr.snapshotTime));
            }
        }
        return out;
    }

    /* ============================================================
     * 內部資料型別 & 轉換
     * ============================================================ */

    /** 輕量快照：時間戳 + (addr -> state) */
    private record SafetySnapshot(int deviceId, Instant snapshotTime, Map<String, Boolean> addrStates) {
        static SafetySnapshot of(int deviceId, Instant t, Map<String, Boolean> s) {
            return new SafetySnapshot(deviceId, t, new LinkedHashMap<>(s));
        }
    }

    /** 將快照轉成強型別 DTO，存入快取用 */
    private SafetyDeviceStatus toDeviceStatus(int deviceId, String deviceName, SafetySnapshot s) {
        SafetyDeviceStatus dto = new SafetyDeviceStatus();
        dto.setDeviceId(deviceId);
        dto.setDeviceName(deviceName);
        dto.setSnapshotTime(s.snapshotTime());
        dto.setAvailable(true);
        dto.setComplete(true);   // 若你有「讀區不完整」情況，可在這裡改為 false
        dto.setStale(false);
        s.addrStates().forEach(dto::putState);
        return dto;
    }

    /** 小工具：計算秒數差（避免引入 java.time.Duration 的小雜訊） */
    private static final class DurationSeconds {
        static long between(Instant a, Instant b) {
            return Math.abs(b.toEpochMilli() - a.toEpochMilli()) / 1000;
        }
    }
}